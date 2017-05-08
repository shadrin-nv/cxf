/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.rs.security.jose.jaxrs.multipart;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MediaType;

import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.utils.multipart.AttachmentUtils;
import org.apache.cxf.rs.security.jose.common.JoseConstants;
import org.apache.cxf.rs.security.jose.jws.JwsDetachedSignature;
import org.apache.cxf.rs.security.jose.jws.JwsHeaders;
import org.apache.cxf.rs.security.jose.jws.JwsSignature;
import org.apache.cxf.rs.security.jose.jws.JwsSignatureProvider;
import org.apache.cxf.rs.security.jose.jws.JwsUtils;

public class JwsMultipartClientRequestFilter implements ClientRequestFilter {

    private JwsSignatureProvider sigProvider;
    private boolean supportSinglePartOnly = true;

    public void setSignatureProvider(JwsSignatureProvider signatureProvider) {
        this.sigProvider = signatureProvider;
    }
    
    @Override
    public void filter(ClientRequestContext ctx) throws IOException {
        MediaType contentType = ctx.getMediaType();
        if (contentType != null && contentType.getType().equals("multipart")) {
            Object rootEntity = ctx.getEntity();
            List<Object> parts = null;
            
            if (rootEntity instanceof MultipartBody) {
                parts = CastUtils.cast(((MultipartBody)rootEntity).getAllAttachments());
            } else {
                parts = new ArrayList<Object>();
                if (rootEntity instanceof List) {
                    List<Object> entityList = CastUtils.cast((List<?>)rootEntity);
                    parts.addAll(entityList);
                } else {
                    parts.add(rootEntity);
                }
            }
            if (supportSinglePartOnly && parts.size() > 1) {
                throw new ProcessingException("Single part only is supported");
            }
            
            JwsHeaders headers = new JwsHeaders();
            JwsSignatureProvider theSigProvider = sigProvider != null ? sigProvider
                : JwsUtils.loadSignatureProvider(headers, true);
            JwsSignature jwsSignature = theSigProvider.createJwsSignature(headers);
            AttachmentUtils.addMultipartOutFilter(new JwsMultipartSignatureOutFilter(jwsSignature));
            
            
            JwsDetachedSignature jws = new JwsDetachedSignature(headers, jwsSignature);
            
            Attachment jwsPart = new Attachment("signature", JoseConstants.MEDIA_TYPE_JOSE, jws);
            parts.add(jwsPart);
            ctx.setEntity(parts);
        }
        
    }

    public void setSupportSinglePartOnly(boolean supportSinglePartOnly) {
        this.supportSinglePartOnly = supportSinglePartOnly;
    }
}
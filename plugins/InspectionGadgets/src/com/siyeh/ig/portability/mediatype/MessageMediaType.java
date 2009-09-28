/*
 * Copyright 2006 Mark Scott
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.portability.mediatype;

import org.jetbrains.annotations.NonNls;

public enum MessageMediaType
{
    CPIM("message/CPIM"),
    DELIVERY_STATUS("message/delivery-status"),
    DISPOSITION_NOTIFICATION("message/disposition-notification"),
    EXAMPLE("message/example"),
    EXTERNAL_BODY("message/external-body"),
    HTTP("message/http"),
    NEWS("message/news"),
    PARTIAL("message/partial"),
    RFC822("message/rfc822"),
    S_HTTP("message/s-http"),
    SIP("message/sip"),
    SIPFRAG("message/sipfrag"),
    TRACKING_STATUS("message/tracking-status");

    private final String contentType;

    MessageMediaType(@NonNls String contentType)
    {
        this.contentType = contentType;
    }

    public String toString()
    {
        return contentType;
    }
}
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

public enum MultipartMediaType
{
    ALTERNATIVE("multipart/alternative"),
    APPLEDOUBLE("multipart/appledouble"),
    BYTERANGES("multipart/byteranges"),
    DIGEST("multipart/digest"),
    ENCRYPTED("multipart/encrypted"),
    EXAMPLE("multipart/example"),
    FORM_DATA("multipart/form-data"),
    HEADER_SET("multipart/header-set"),
    MIXED("multipart/mixed"),
    PARALLEL("multipart/parallel"),
    RELATED("multipart/related"),
    REPORT("multipart/report"),
    SIGNED("multipart/signed"),
    VOICE_MESSAGE("multipart/voice-message");

    private final String contentType;

    MultipartMediaType(@NonNls String contentType)
    {
        this.contentType = contentType;
    }

    public String toString()
    {
        return contentType;
    }
}
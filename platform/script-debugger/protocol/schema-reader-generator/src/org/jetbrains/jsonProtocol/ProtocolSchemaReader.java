// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jetbrains.jsonProtocol;

import org.jetbrains.io.JsonReaderEx;
import org.chromium.protocolReader.JsonParseMethod;
import org.jetbrains.jsonProtocol.ProtocolMetaModel.Root;

import java.io.IOException;

public interface ProtocolSchemaReader {
  @JsonParseMethod
  Root parseRoot(JsonReaderEx reader) throws IOException;
}
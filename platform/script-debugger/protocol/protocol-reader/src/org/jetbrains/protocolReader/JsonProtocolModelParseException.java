// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jetbrains.protocolReader;

/**
 * Signals that JSON model has some problem in it.
 */
public class JsonProtocolModelParseException extends RuntimeException {

  public JsonProtocolModelParseException(String message, Throwable cause) {
    super(message, cause);
  }

  public JsonProtocolModelParseException(String message) {
    super(message);
  }
}

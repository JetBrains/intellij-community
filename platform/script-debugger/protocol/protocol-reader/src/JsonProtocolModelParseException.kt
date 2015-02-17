// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jetbrains.protocolReader

/**
 * Signals that JSON model has some problem in it.
 */

public fun JsonProtocolModelParseException(message: String, cause: Throwable): JsonProtocolModelParseException {
  val __ = JsonProtocolModelParseException()
  `super`(message, cause)
  return __
}

public fun JsonProtocolModelParseException(message: String): JsonProtocolModelParseException {
  val __ = JsonProtocolModelParseException()
  `super`(message)
  return __
}

public class JsonProtocolModelParseException : RuntimeException()

// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jetbrains.protocolReader

/**
 * An internal facility for navigating from object of base type to object of subtype. Used only
 * when user wants to parse JSON object as subtype.
 */
abstract class SubtypeCaster(private val subtypeRef: TypeRef<*>) {

  abstract fun writeJava(out: TextOutput)

  fun getSubtypeHandler(): TypeWriter<*> {
    return subtypeRef.type
  }
}
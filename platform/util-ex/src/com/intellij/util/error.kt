// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.util.lang.CompoundRuntimeException

fun throwIfNotEmpty(errors: List<Throwable>) {
  val size = errors.size
  if (size == 1) {
    throw errors.first()
  }
  else if (size != 0) {
    throw CompoundRuntimeException(errors)
  }
}
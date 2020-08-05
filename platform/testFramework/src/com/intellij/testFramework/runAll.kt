// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.util.lang.CompoundRuntimeException

inline fun MutableList<Throwable>.catchAndStoreExceptions(executor: () -> Unit) {
  try {
    executor()
  }
  catch (e: CompoundRuntimeException) {
    addAll(e.exceptions)
  }
  catch (e: Throwable) {
    add(e)
  }
}

fun List<Throwable>.throwIfNotEmpty() {
  if (isNotEmpty()) {
    throw if (size == 1) first() else CompoundRuntimeException(this)
  }
}
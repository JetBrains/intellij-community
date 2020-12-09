// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

fun <T : Any, R : Any> Query<T>.mappingNotNull(f: (T) -> R?): Query<R> {
  return transforming { t: T ->
    val r: R? = f(t)
    if (r == null) {
      emptyList()
    }
    else {
      listOf(r)
    }
  }
}

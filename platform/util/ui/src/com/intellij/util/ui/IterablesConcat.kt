// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import org.jetbrains.annotations.ApiStatus.Internal

object IterablesConcat {
  @JvmStatic
  @Internal
  fun<T> concat(left: Iterable<T>, right: Iterable<T>): Iterable<T> {
    var sequence = left.asSequence()
    sequence += right.asSequence()
    return sequence.asIterable()
  }
}

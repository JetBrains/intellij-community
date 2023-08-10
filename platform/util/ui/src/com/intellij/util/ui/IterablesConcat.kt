// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
internal object IterablesConcat {
  @JvmStatic
  fun<T> concat(left: Iterable<T>, right: Iterable<T>): Iterable<T> = left + right
}

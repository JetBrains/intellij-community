// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.fastutil.ints

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Deprecated(
  "This API is temporary multiplatform shim. Please make sure you are not using it by accident",
  replaceWith = ReplaceWith("it.unimi.dsi.fastutil.ints.MutableIntSet"),
  level = DeprecationLevel.WARNING
)
interface MutableIntSet: IntSet {
  fun add(key: Int): Boolean
  fun remove(key: Int): Boolean
}
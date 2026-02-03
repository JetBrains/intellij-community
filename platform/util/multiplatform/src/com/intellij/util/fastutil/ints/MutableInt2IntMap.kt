// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.fastutil.ints

import org.jetbrains.annotations.ApiStatus

@Deprecated(
  "This API is temporary multiplatform shim. Please make sure you are not using it by accident",
  replaceWith = ReplaceWith("it.unimi.dsi.fastutil.ints.MutableInt2IntMap"),
  level = DeprecationLevel.WARNING
)
@ApiStatus.Internal
interface MutableInt2IntMap : Int2IntMap {
  fun put(key: Int, value: Int): Int
  fun remove(key: Int): Int

  operator fun set(key: Int, value: Int): Int = put(key, value)
}
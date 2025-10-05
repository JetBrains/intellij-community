// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.fastutil.ints

import org.jetbrains.annotations.ApiStatus

@Deprecated(
  "This API is temporary multiplatform shim. Please make sure you are not using it by accident",
  replaceWith = ReplaceWith("it.unimi.dsi.fastutil.ints.Int2IntMap"),
  level = DeprecationLevel.WARNING
)
@ApiStatus.Internal
interface Int2IntMap {
  val size: Int
  val keys: IntIterator
  val values: IntIterator
  val entries: Iterator<Int2IntEntry>

  operator fun get(key: Int): Int
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb

fun interface SerializationFilter {
  fun accepts(accessor: Accessor, bean: Any): Boolean
}

abstract class Converter<T> {
  abstract fun fromString(value: String): T?

  abstract fun toString(value: T): String?
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.extensions

import org.jetbrains.annotations.ApiStatus

/**
 * A key that allows to get extensions registered under the corresponding [name].
 */
@ApiStatus.Experimental
class ExtensionPointKey<T : Any> internal constructor(val name: String, unused: Any?) {
  override fun equals(other: Any?): Boolean =
    this === other || (other is ExtensionPointKey<*> && name == other.name)

  override fun hashCode(): Int =
    name.hashCode()

  override fun toString(): String =
    "ExtensionKey($name)"
}

@ApiStatus.Experimental
fun <T : Any> ExtensionPointKey(name: String): ExtensionPointKey<T> = ExtensionPointKey(name, null)

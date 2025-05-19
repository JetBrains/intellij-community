// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.extensions

import org.jetbrains.annotations.ApiStatus

/**
 * Language allows associate extension points with it.
 *
 * See [ExtensionSupport.getLanguageExtensions]
 */
@ApiStatus.Experimental
class SyntaxLanguage internal constructor(val id: String, unused: Any?) {
  override fun equals(other: Any?): Boolean =
    other === this || (other is SyntaxLanguage && other.id == id)

  override fun hashCode(): Int =
    id.hashCode()

  override fun toString(): String =
    "SyntaxLanguage($id)"
}

fun SyntaxLanguage(id: String): SyntaxLanguage {
  return SyntaxLanguage(id, unused = null)
}

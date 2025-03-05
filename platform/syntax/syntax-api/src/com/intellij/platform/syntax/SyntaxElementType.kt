// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax

import org.jetbrains.annotations.ApiStatus

/**
 * A class defining a token or node type.
 *
 * [debugName] is used only for debug purposes.
 */
@ApiStatus.Experimental
class SyntaxElementType(
  private val debugName: String,
) {
  override fun toString(): String = debugName
}
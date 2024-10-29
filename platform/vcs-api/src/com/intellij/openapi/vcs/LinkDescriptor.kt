// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

/**
 * Represent a link in some text.
 *
 * [range] - E.g., link text range.
 * E.g., a substring range of corresponding text with a link.
 */
@ApiStatus.Experimental
interface LinkDescriptor {
  val range: TextRange
}

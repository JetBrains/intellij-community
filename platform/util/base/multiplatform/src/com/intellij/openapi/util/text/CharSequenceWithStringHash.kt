// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text

import org.jetbrains.annotations.ApiStatus

/**
 * This interface implementations must have `hashCode` values equal to those for String.
 *
 * @see com.intellij.openapi.util.text.StringUtil.stringHashCode
 */
@ApiStatus.Internal
interface CharSequenceWithStringHash : CharSequence {
  override fun hashCode(): Int
}
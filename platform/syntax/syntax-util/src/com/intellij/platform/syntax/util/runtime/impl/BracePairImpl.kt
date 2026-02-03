// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime.impl

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.util.runtime.BracePair

internal data class BracePairImpl(
  override val leftBrace: SyntaxElementType?,
  override val rightBrace: SyntaxElementType?,
  private val structural: Boolean,
) : BracePair
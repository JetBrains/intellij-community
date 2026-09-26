// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.util.runtime.impl.BracePairImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface BracePair {
  val leftBrace: SyntaxElementType?
  val rightBrace: SyntaxElementType?
}

fun BracePair(
  leftBrace: SyntaxElementType?,
  rightBrace: SyntaxElementType?,
  structural: Boolean,
): BracePair = BracePairImpl(leftBrace, rightBrace, structural)

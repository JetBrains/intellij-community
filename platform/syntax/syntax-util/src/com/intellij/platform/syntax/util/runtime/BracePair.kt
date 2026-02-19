// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.util.runtime

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.util.runtime.impl.BracePairImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface BracePair {
  val leftBrace: SyntaxElementType?
  val rightBrace: SyntaxElementType?
}

@ApiStatus.Experimental
fun BracePair(
  leftBrace: SyntaxElementType?,
  rightBrace: SyntaxElementType?,
  structural: Boolean,
): BracePair = BracePairImpl(leftBrace, rightBrace, structural)

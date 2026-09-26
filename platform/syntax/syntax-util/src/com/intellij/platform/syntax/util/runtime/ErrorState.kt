// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime

import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface ErrorState {
  fun getExpected(position: Int, expected: Boolean): String

  fun clearVariants(frame: Frame?)

  fun clearVariants(expected: Boolean, start: Int)

  fun typeExtends(child: SyntaxElementType?, parent: SyntaxElementType?): Boolean
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.runtime

import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface SyntaxParserRuntimeFactory {
  fun buildParserUtils(builder: SyntaxTreeBuilder): SyntaxGeneratedParserRuntime
}
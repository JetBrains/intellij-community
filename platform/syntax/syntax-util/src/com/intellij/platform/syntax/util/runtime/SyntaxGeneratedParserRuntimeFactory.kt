// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.util.runtime

import com.intellij.platform.syntax.Logger
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.util.runtime.impl.MAX_RECURSION_LEVEL
import com.intellij.platform.syntax.util.runtime.impl.SyntaxGeneratedParserRuntimeImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun SyntaxGeneratedParserRuntime(
  syntaxBuilder: SyntaxTreeBuilder,
  parserUserState: ParserUserState?,
  isLanguageCaseSensitive: Boolean,
  braces: Collection<BracePair>,
  logger: Logger,
  maxRecursionDepth: Int = MAX_RECURSION_LEVEL,
): SyntaxGeneratedParserRuntime = SyntaxGeneratedParserRuntimeImpl(
  syntaxBuilder = syntaxBuilder,
  maxRecursionDepth = maxRecursionDepth,
  isLanguageCaseSensitive = isLanguageCaseSensitive,
  braces = braces,
  logger = logger,
  parserUserState = parserUserState
)

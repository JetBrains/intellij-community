// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime.impl

import com.intellij.platform.syntax.Logger
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.util.runtime.BracePair
import com.intellij.platform.syntax.util.runtime.ParserUserState
import com.intellij.platform.syntax.util.runtime.SyntaxGeneratedParserRuntime

internal class SyntaxGeneratedParserRuntimeImpl(
  override val syntaxBuilder: SyntaxTreeBuilder,
  override val maxRecursionDepth: Int,
  internal val isLanguageCaseSensitive: Boolean,
  internal val braces: Collection<BracePair>,
  internal val LOG: Logger,
  override val parserUserState: ParserUserState?,
) : SyntaxGeneratedParserRuntime {
  internal val errorState: ErrorStateImpl = ErrorStateImpl()

  internal lateinit var parser: (SyntaxElementType, SyntaxGeneratedParserRuntime) -> Unit

  override fun init(
    parse: (SyntaxElementType, SyntaxGeneratedParserRuntime) -> Unit,
    extendsSets: Array<SyntaxElementTypeSet>,
  ) {
    parser = parse
    errorState.initState(this, extendsSets)
  }
}
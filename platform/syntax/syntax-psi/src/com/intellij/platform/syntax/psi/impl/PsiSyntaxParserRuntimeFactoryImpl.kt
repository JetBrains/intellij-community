// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.Language
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.psi.createSyntaxGeneratedParserRuntime
import com.intellij.platform.syntax.util.runtime.ParserUserState
import com.intellij.platform.syntax.util.runtime.SyntaxGeneratedParserRuntime
import com.intellij.platform.syntax.util.runtime.SyntaxParserRuntimeFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Use createSyntaxGeneratedParserRuntime",
            ReplaceWith("com.intellij.platform.syntax.psi.createSyntaxGeneratedParserRuntime(builder, extendedState)"))
internal class PsiSyntaxParserRuntimeFactoryImpl(
  private val language: Language,
) : SyntaxParserRuntimeFactory {
  override fun buildParserRuntime(builder: SyntaxTreeBuilder, extendedState: ParserUserState?): SyntaxGeneratedParserRuntime =
    createSyntaxGeneratedParserRuntime(language, builder, extendedState)
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use createSyntaxGeneratedParserRuntime",
            ReplaceWith("com.intellij.platform.syntax.psi.createSyntaxGeneratedParserRuntime(builder, extendedState)"))
fun getSyntaxParserRuntimeFactory(language: Language): SyntaxParserRuntimeFactory = PsiSyntaxParserRuntimeFactoryImpl(language)
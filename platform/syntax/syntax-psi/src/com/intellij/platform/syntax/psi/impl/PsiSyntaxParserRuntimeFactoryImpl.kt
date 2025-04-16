// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.Language;
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.psi.LanguageSyntaxDefinitions
import com.intellij.platform.syntax.util.runtime.SyntaxGeneratedParserRuntime
import com.intellij.platform.syntax.util.runtime.SyntaxParserRuntimeFactory

internal class PsiSyntaxParserRuntimeFactoryImpl(private val language: Language) : SyntaxParserRuntimeFactory {

  override fun buildParserUtils(builder: SyntaxTreeBuilder): SyntaxGeneratedParserRuntime {
    val languageInformer = LanguageSyntaxDefinitions.INSTANCE.forLanguage(language)
    return SyntaxGeneratedParserRuntime(
      syntaxBuilder = builder,
      isCaseSensitive = language.isCaseSensitive,
      braces = languageInformer.getPairedBraces(),
      maxRecursionDepth = 1000,
    )
  }
}

fun getSyntaxParserRuntimeFactory(language: Language):SyntaxParserRuntimeFactory = PsiSyntaxParserRuntimeFactoryImpl(language)
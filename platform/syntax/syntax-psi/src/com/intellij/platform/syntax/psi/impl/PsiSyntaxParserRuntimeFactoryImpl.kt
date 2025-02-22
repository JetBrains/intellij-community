// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.runtime.SyntaxGeneratedParserRuntimeBase
import com.intellij.platform.syntax.runtime.SyntaxParserRuntimeFactory

class PsiSyntaxParserRuntimeFactoryImpl(private val languageInformer: SyntaxGeneratedParserRuntimeBase.LanguageInfoProvider) : SyntaxParserRuntimeFactory {

  companion object {
    fun createFromBuilder(builder: SyntaxTreeBuilder): SyntaxParserRuntimeFactory {
      assert(builder is SyntaxGeneratedParserRuntimeBase.LanguageInfoProvider)
      return PsiSyntaxParserRuntimeFactoryImpl(
        languageInformer = builder as SyntaxGeneratedParserRuntimeBase.LanguageInfoProvider,
      )
    }
  }

  override fun buildParserUtils(): SyntaxGeneratedParserRuntimeBase {
    return SyntaxGeneratedParserRuntimeBase(
      bundle = AnalysisBundleAdapted,
      lexer = languageInformer.getLexer(),
      isLanguageCaseSensitive = languageInformer.isLanguageCaseSensitive(),
      braces = languageInformer.getBraces()
    )
  }
}
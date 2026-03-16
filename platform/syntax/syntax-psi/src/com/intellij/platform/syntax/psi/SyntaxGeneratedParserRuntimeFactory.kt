// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.util.runtime.GrammarKitLanguageDefinition
import com.intellij.platform.syntax.util.runtime.ParserUserState
import com.intellij.platform.syntax.util.runtime.SyntaxGeneratedParserRuntime

fun createSyntaxGeneratedParserRuntime(
  language: Language,
  builder: SyntaxTreeBuilder,
  state: ParserUserState? = null,
): SyntaxGeneratedParserRuntime {
  val languageInformer = LanguageSyntaxDefinitions.INSTANCE.forLanguage(language)
  if (languageInformer !is GrammarKitLanguageDefinition) {
    error("Language $language is not supported by GrammarKit")
  }

  return SyntaxGeneratedParserRuntime(
    syntaxBuilder = builder,
    parserUserState = state,
    isLanguageCaseSensitive = language.isCaseSensitive,
    braces = languageInformer.getPairedBraces(),
    logger = logger<SyntaxGeneratedParserRuntime>().asSyntaxLogger(),
  )
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.lexer.performLexing
import com.intellij.platform.syntax.psi.asSyntaxLogger
import com.intellij.platform.syntax.psi.registerLexing
import kotlin.time.measureTimedValue

internal fun performLexingIfNecessary(cachedLexemes: TokenList?, lexer: Lexer, text: CharSequence, language: Language): TokenList {
  if (cachedLexemes != null) return cachedLexemes

  val lexemes = measureTimedValue {
    performLexing(text, lexer, ProgressManager::checkCanceled, log)
  }

  registerLexing(language, text.length.toLong(), lexemes.duration.inWholeNanoseconds)

  return lexemes.value
}

private val log = fileLogger().asSyntaxLogger()
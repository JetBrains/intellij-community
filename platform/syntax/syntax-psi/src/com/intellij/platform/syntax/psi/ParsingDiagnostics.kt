// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ParsingDiagnostics")

package com.intellij.platform.syntax.psi

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.psi.ParsingDiagnostics.ParserDiagnosticsHandler
import kotlin.time.measureTime

fun registerParse(builder: PsiSyntaxBuilder, language: Language, parsingTimeNs: Long) {
  registerParse(builder.getSyntaxTreeBuilder(), language, parsingTimeNs)
}

fun registerParse(builder: SyntaxTreeBuilder, language: Language, parsingTimeNs: Long) {
  val handler = ApplicationManager.getApplication().getService(ParserDiagnosticsHandler::class.java)
  if (handler is ParsingDiagnosticsHandler) {
    handler.registerParse(builder, language, parsingTimeNs)
  }
}

fun <T> registerParse(builder: PsiSyntaxBuilder, language: Language, parsingBlock: () -> T): T =
  registerParse(builder.getSyntaxTreeBuilder(), language, parsingBlock)

fun <T> registerParse(builder: SyntaxTreeBuilder, language: Language, parsingBlock: () -> T): T {
  val result: T
  val duration = measureTime {
    result = parsingBlock()
  }
  registerParse(builder, language, duration.inWholeMilliseconds)
  return result
}


fun registerLexing(language: Language, textLength: Long, lexingTimeNs: Long) {
  val handler = ApplicationManager.getApplication().getService(ParserDiagnosticsHandler::class.java)
  if (handler is ParsingDiagnosticsHandler) {
    handler.registerLexing(language, textLength, lexingTimeNs)
  }
}

interface ParsingDiagnosticsHandler {
  fun registerParse(builder: SyntaxTreeBuilder, language: Language, parsingTimeNs: Long)

  fun registerLexing(language: Language, textLength: Long, lexingTimeNs: Long)
}
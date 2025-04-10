// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
@file:JvmName("ParsingDiagnostics")

package com.intellij.platform.syntax.psi

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.psi.ParsingDiagnostics.ParserDiagnosticsHandler
import kotlin.jvm.JvmName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun registerParse(builder: PsiSyntaxBuilder, language: Language, parsingTimeNs: Long) {
  registerParse(builder.getSyntaxTreeBuilder(), language, parsingTimeNs)
}

@ApiStatus.Experimental
fun registerParse(builder: SyntaxTreeBuilder, language: Language, parsingTimeNs: Long) {
  val handler = ApplicationManager.getApplication().getService(ParserDiagnosticsHandler::class.java)
  if (handler is ParsingDiagnosticsHandler) {
    handler.registerParse(builder, language, parsingTimeNs)
  }
}

@ApiStatus.Experimental
interface ParsingDiagnosticsHandler {
  fun registerParse(builder: SyntaxTreeBuilder, language: Language, parsingTimeNs: Long)
}
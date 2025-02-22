//// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl
//
//import com.intellij.lang.SyntaxTreeBuilder
//import com.intellij.platform.syntax.lexer.Lexer
//import com.intellij.platform.syntax.runtime.SyntaxGeneratedParserRuntimeBase
//import com.intellij.platform.syntax.runtime.SyntaxParserRuntimeFactory
//import com.intellij.psi.impl.source.resolve.FileContextUtil.CONTAINING_FILE_KEY
//
//class PsiSyntaxParserRuntimeFactory(val isCaseSensitive: Boolean, val lexer: Lexer) : SyntaxParserRuntimeFactory {
//
//  companion object {
//    fun createFromBuilder(builder: SyntaxTreeBuilder){
//      if (builder is ParsingTreeBuilder){
//
//      }
//      val file = builder.getUserData(CONTAINING_FILE_KEY)
//      val languages = file?.language
//      val isCaseSensitive = languages?.isCaseSensitive != false
//
//      return PsiSyntaxParserRuntimeFactory(isCaseSensitive, builder.)
//    }
//  }
//
//  override fun buildParserUtils(): SyntaxGeneratedParserRuntimeBase {
//    return SyntaxGeneratedParserRuntimeBase(
//      lexer = lexer,
//      bundle = AnalysisBundleAdapted,
//      braces =
//    )
//  }
//}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IFileElementType

class SyntaxFileElementType(language: Language) : IFileElementType(language) {
  override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
    val builderFactory = PsiSyntaxBuilderFactory.getInstance()
    val syntaxLanguageDefinition = LanguageSyntaxDefinitions.INSTANCE.forLanguage(language)
    val lexer = syntaxLanguageDefinition.createLexer()
    val syntaxBuilder = builderFactory.createBuilder(
      chameleon = chameleon,
      lexer = lexer,
      lang = language,
      text = chameleon.getChars()
    )

    val root = registerParse(syntaxBuilder, language) {
      syntaxLanguageDefinition.parse(syntaxBuilder.getSyntaxTreeBuilder())
      syntaxBuilder.getTreeBuilt()
    }
    return root.getFirstChildNode()
  }
}

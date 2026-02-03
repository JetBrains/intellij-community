// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.platform.syntax.psi.ElementTypeConverters.getConverter
import com.intellij.platform.syntax.psi.impl.getSyntaxParserRuntimeFactory
import com.intellij.platform.syntax.util.runtime.GrammarKitLanguageDefinition
import com.intellij.platform.syntax.util.runtime.SyntaxGeneratedParserRuntime
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IFileElementType

class SyntaxGrammarKitFileElementType(language: Language) : IFileElementType(language) {

  override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
    val builderFactory = PsiSyntaxBuilderFactory.getInstance()
    val elementType = chameleon.getElementType()
    val syntaxLanguageDefinition = LanguageSyntaxDefinitions.INSTANCE.forLanguage(language) as? GrammarKitLanguageDefinition 
                                   ?: throw IllegalStateException("Failed to cast LanguageSyntaxDefinition for language: $language — to GrammarKitLanguageDefinition")
    val lexer = syntaxLanguageDefinition.createLexer()
    val syntaxBuilder = builderFactory.createBuilder(chameleon,
                                                     lexer,
                                                     language,
                                                     chameleon.getChars())
    val converter = getConverter(language)
    val convertedElement = converter.convert(elementType) ?: throw IllegalStateException("Failed convert element type: $elementType. Converter: Converter: ${converter}")
    val parserRuntime =
      getSyntaxParserRuntimeFactory(language)
        .buildParserRuntime(syntaxBuilder.getSyntaxTreeBuilder(),
                            createExtendedParserUserState())
    val startTime = System.nanoTime()
    syntaxLanguageDefinition.parse(convertedElement, parserRuntime)
    val root = syntaxBuilder.getTreeBuilt()
    registerParse(syntaxBuilder, language, System.nanoTime() - startTime)
    return root.getFirstChildNode() 
  }
  
  open fun createExtendedParserUserState(): SyntaxGeneratedParserRuntime.ParserUserState? = null
  
}
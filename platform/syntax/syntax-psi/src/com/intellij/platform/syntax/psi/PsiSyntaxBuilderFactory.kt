// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.lang.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Pair
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.parser.WhitespaceOrCommentBindingPolicy
import com.intellij.platform.syntax.psi.impl.LazyParseableToken
import com.intellij.platform.syntax.psi.impl.PsiSyntaxBuilderImpl
import com.intellij.platform.syntax.psi.impl.extractCachedLexemes
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.text.BlockSupport
import com.intellij.psi.tree.IElementType

@Service(Service.Level.APP)
class PsiSyntaxBuilderFactory {
  fun createBuilder(
    chameleon: ASTNode,
    lexer: Lexer? = null,
    lang: Language = chameleon.elementType.language,
    text: CharSequence = chameleon.chars,
  ): PsiSyntaxBuilder {
    val parserDefinition = getParserDefinition(lang, chameleon.getElementType())
    val tokenConverter = getConverter(lang, chameleon.getElementType())
    val syntaxDefinition = LanguageSyntaxDefinitions.INSTANCE.forLanguage(lang) ?: throw IllegalStateException("No SyntaxDefinition for language: $lang")
    val actualLexer = lexer ?: syntaxDefinition.getLexer()

    return PsiSyntaxBuilderImpl(
      file = SharedImplUtil.getContainingFile(chameleon),
      parserDefinition = parserDefinition,
      syntaxDefinition = syntaxDefinition,
      lexer = actualLexer,
      charTable = SharedImplUtil.findCharTableByTree(chameleon),
      text = text,
      originalTree = Pair.getFirst(chameleon.getUserData(BlockSupport.TREE_TO_BE_REPARSED)),
      lastCommittedText = Pair.getSecond(chameleon.getUserData(BlockSupport.TREE_TO_BE_REPARSED)),
      parentLightTree = null,
      startOffset = 0,
      cachedLexemes = extractCachedLexemes(chameleon),
      tokenConverter = tokenConverter,
      opaquePolicy = null,
      whitespaceOrCommentBindingPolicy = DefaultWhitespaceOrCommentPolicy(tokenConverter),
    )
  }

  fun createBuilder(
    chameleon: LighterLazyParseableNode,
    lexer: Lexer? = null,
    lang: Language = chameleon.tokenType.language,
    text: CharSequence = chameleon.text,
  ): PsiSyntaxBuilder {
    val parserDefinition = getParserDefinition(null, chameleon.getTokenType())
    val tokenConverter = getConverter(lang, chameleon.getTokenType())
    val languageSyntaxDefinition = LanguageSyntaxDefinitions.INSTANCE.forLanguage(lang)
    val actualLexer = lexer ?: languageSyntaxDefinition.getLexer()
    return PsiSyntaxBuilderImpl(
      file = chameleon.getContainingFile(),
      parserDefinition = parserDefinition,
      syntaxDefinition = languageSyntaxDefinition,
      lexer = actualLexer,
      charTable = chameleon.getCharTable(),
      text = text,
      originalTree = null,
      lastCommittedText = null,
      parentLightTree = (chameleon as LazyParseableToken).parentStructure,
      startOffset = chameleon.startOffset,
      cachedLexemes = extractCachedLexemes(chameleon),
      tokenConverter = tokenConverter,
      opaquePolicy = null,
      whitespaceOrCommentBindingPolicy = DefaultWhitespaceOrCommentPolicy(tokenConverter),
    )
  }

  private fun getParserDefinition(language: Language?, tokenType: IElementType): ParserDefinition {
    val adjusted = language ?: tokenType.language
    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(adjusted)
    if (parserDefinition == null) {
      throw AssertionError("ParserDefinition absent for language: '${adjusted.id}' (${adjusted.javaClass.getName()}), for elementType: '${tokenType.debugName}' (${tokenType.javaClass.getName()})")
    }
    return parserDefinition
  }

  private fun getConverter(language: Language?, tokenType: IElementType): ElementTypeConverter {
    val adjusted = language ?: tokenType.language
    return ElementTypeConverters.getConverter(adjusted)
  }

  companion object {
    @JvmStatic
    fun getInstance(): PsiSyntaxBuilderFactory = service<PsiSyntaxBuilderFactory>()
  }
}

private class DefaultWhitespaceOrCommentPolicy(val tokenConverter: ElementTypeConverter) : WhitespaceOrCommentBindingPolicy {
  override fun isLeftBound(elementType: SyntaxElementType): Boolean =
    tokenConverter.convertNotNull(elementType).isLeftBound
}


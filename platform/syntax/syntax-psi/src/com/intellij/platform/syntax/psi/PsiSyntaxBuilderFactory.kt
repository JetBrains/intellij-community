// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.LighterLazyParseableNode
import com.intellij.lang.ParserDefinition
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Pair
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.parser.WhitespaceOrCommentBindingPolicy
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory.Companion.getInstance
import com.intellij.platform.syntax.psi.impl.LazyParseableToken
import com.intellij.platform.syntax.psi.impl.PsiSyntaxBuilderImpl
import com.intellij.platform.syntax.psi.impl.extractCachedLexemes
import com.intellij.platform.syntax.psi.impl.performLexingIfNecessary
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.text.BlockSupport
import com.intellij.psi.tree.IElementType

interface PsiSyntaxBuilderFactory {
  fun createBuilder(
    chameleon: ASTNode,
    lexer: Lexer? = null,
    lang: Language = chameleon.elementType.language,
    text: CharSequence = chameleon.chars,
  ): PsiSyntaxBuilder

  fun createBuilder(
    chameleon: LighterLazyParseableNode,
    lexer: Lexer? = null,
    lang: Language = chameleon.tokenType.language,
    text: CharSequence = chameleon.text,
  ): PsiSyntaxBuilder

  companion object {
    @JvmStatic
    fun getInstance(): PsiSyntaxBuilderFactory = service<PsiSyntaxBuilderFactory>()

    /**
     * Use this method to access the default [PsiSyntaxBuilderFactory] implementation if [PsiSyntaxBuilderFactory] service is overridden.
     * Otherwise, please use [getInstance].
     */
    fun defaultBuilderFactory(): PsiSyntaxBuilderFactory = service<DefaultBuilderFactoryHolder>().factory
  }
}

@Service(Service.Level.APP)
private class DefaultBuilderFactoryHolder {
  val factory = PsiSyntaxBuilderFactoryImpl()
}

internal class PsiSyntaxBuilderFactoryImpl : PsiSyntaxBuilderFactory {
  override fun createBuilder(
    chameleon: ASTNode,
    lexer: Lexer?,
    lang: Language,
    text: CharSequence,
  ): PsiSyntaxBuilder {
    val parserDefinition = getParserDefinition(lang, chameleon.getElementType())
    val tokenConverter = getConverter(lang, chameleon.getElementType())
    val syntaxDefinition = LanguageSyntaxDefinitions.INSTANCE.forLanguage(lang)
                           ?: throw IllegalStateException("No SyntaxDefinition for language: $lang")
    val actualLexer = lexer ?: syntaxDefinition.createLexer()
    val cachedLexemes = extractCachedLexemes(chameleon)
    val lexingResult = performLexingIfNecessary(cachedLexemes, actualLexer, text, lang)

    return PsiSyntaxBuilderImpl(
      file = SharedImplUtil.getContainingFile(chameleon),
      parserDefinition = parserDefinition,
      syntaxDefinition = syntaxDefinition,
      tokenList = lexingResult,
      charTable = SharedImplUtil.findCharTableByTree(chameleon),
      text = text,
      originalTree = Pair.getFirst(chameleon.getUserData(BlockSupport.TREE_TO_BE_REPARSED)),
      lastCommittedText = Pair.getSecond(chameleon.getUserData(BlockSupport.TREE_TO_BE_REPARSED)),
      parentLightTree = null,
      startOffset = 0,
      tokenConverter = tokenConverter,
      opaquePolicy = null,
      whitespaceOrCommentBindingPolicy = DefaultWhitespaceOrCommentPolicy(tokenConverter),
    )
  }

  override fun createBuilder(
    chameleon: LighterLazyParseableNode,
    lexer: Lexer?,
    lang: Language,
    text: CharSequence,
  ): PsiSyntaxBuilder {
    val parserDefinition = getParserDefinition(null, chameleon.getTokenType())
    val tokenConverter = getConverter(lang, chameleon.getTokenType())
    val languageSyntaxDefinition = LanguageSyntaxDefinitions.INSTANCE.forLanguage(lang)
    val actualLexer = lexer ?: languageSyntaxDefinition.createLexer()
    val cachedLexemes = extractCachedLexemes(chameleon)
    val lexingResult = performLexingIfNecessary(cachedLexemes, actualLexer, text, lang)
    return PsiSyntaxBuilderImpl(
      file = chameleon.getContainingFile(),
      parserDefinition = parserDefinition,
      syntaxDefinition = languageSyntaxDefinition,
      charTable = chameleon.getCharTable(),
      text = text,
      originalTree = null,
      lastCommittedText = null,
      parentLightTree = (chameleon as LazyParseableToken).parentStructure,
      startOffset = chameleon.startOffset,
      tokenList = lexingResult,
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
}

private class DefaultWhitespaceOrCommentPolicy(val tokenConverter: ElementTypeConverter) : WhitespaceOrCommentBindingPolicy {
  override fun isLeftBound(elementType: SyntaxElementType): Boolean =
    tokenConverter.convertNotNull(elementType).isLeftBound
}


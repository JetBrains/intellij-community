// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Pair
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.parser.WhitespaceOrCommentBindingPolicy
import com.intellij.platform.syntax.psi.*
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.text.BlockSupport
import com.intellij.psi.tree.IElementType

@Service(Service.Level.APP)
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
    val actualLexer = lexer ?: syntaxDefinition.getLexer()

    return PsiSyntaxBuilderImpl(
      file = SharedImplUtil.getContainingFile(chameleon),
      parserDefinition = parserDefinition,
      syntaxDefinition = syntaxDefinition,
      lexer = actualLexer,
      charTable = SharedImplUtil.findCharTableByTree(chameleon),
      text = text,
      originalTree = com.intellij.openapi.util.Pair.getFirst(chameleon.getUserData(BlockSupport.TREE_TO_BE_REPARSED)),
      lastCommittedText = Pair.getSecond(chameleon.getUserData(BlockSupport.TREE_TO_BE_REPARSED)),
      parentLightTree = null,
      startOffset = 0,
      cachedLexemes = extractCachedLexemes(chameleon),
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
    val converters = ElementTypeConverters.instance.allForLanguage(adjusted)
    if (converters.isEmpty()) {
      throw AssertionError(
        "ElementTypeConverter absent for language: '${adjusted.id}' (${adjusted.javaClass.getName()}), for elementType: '${tokenType.debugName}' (${tokenType.javaClass.getName()})")
    }

    if (converters.size == 1) {
      return converters.first()
    }
    return object : ElementTypeConverter {
      override fun convert(type: IElementType): SyntaxElementType? {
        converters.forEach { converter ->
          converter.convert(type)?.let { return it }
        }
        return null
      }

      override fun convert(type: SyntaxElementType): IElementType? {
        converters.forEach { converter ->
          converter.convert(type)?.let { return it }
        }
        return null
      }
    }
  }
}

private class DefaultWhitespaceOrCommentPolicy(val tokenConverter: ElementTypeConverter) : WhitespaceOrCommentBindingPolicy {
  override fun isLeftBound(elementType: SyntaxElementType): Boolean =
    tokenConverter.convertNotNull(elementType).isLeftBound
}

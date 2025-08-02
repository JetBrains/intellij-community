// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.tree

import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.Logger
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.extensions.ExtensionSupport
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.lexer.buildTokenList
import com.intellij.platform.syntax.lexer.performLexing
import com.intellij.platform.syntax.parser.DefaultWhitespaceBindingPolicy
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory
import com.intellij.platform.syntax.util.language.SyntaxElementLanguageProvider
import org.jetbrains.annotations.ApiStatus

fun parse(
  text: CharSequence,
  lexerFactory: () -> Lexer,
  parser: (SyntaxTreeBuilder) -> Unit,
  whitespaces: SyntaxElementTypeSet,
  comments: SyntaxElementTypeSet,
  languageMapper: SyntaxElementLanguageProvider,
  cancellationProvider: CancellationProvider? = null,
  logger: Logger? = null,
): KmpSyntaxNode {
  fun createBuilder(
    text: CharSequence,
    tokens: TokenList,
    startLexemeOffset: Int = 0,
  ) = SyntaxTreeBuilderFactory.builder(
    text,
    tokens,
    whitespaces,
    comments,
  ).withWhitespaceOrCommentBindingPolicy(DefaultWhitespaceBindingPolicy) // todo this is incorrect!
    .withStartOffset(startLexemeOffset)
    .build()

  fun performLexingImpl(
    text: CharSequence,
    lexer: Lexer,
    cancellationProvider: CancellationProvider?,
    logger: Logger?,
  ): TokenList {
    val result = performLexing(
      text,
      lexer,
      cancellationProvider,
      logger,
    )
    val isEmpty = result.tokenCount == 0
    return if (isEmpty) buildTokenList {
      token("", SyntaxTokenTypes.WHITE_SPACE)
    }
    else result
  }

  val lexer = lexerFactory()
  val tokens = performLexingImpl(
    text,
    lexer,
    cancellationProvider,
    logger,
  )
  val builder = createBuilder(text, tokens)
  parser(builder)
  val markers = builder.toAstMarkers()
  return KmpSyntaxNode.root(
    text,
    markers,
    tokens = builder.tokens,
    languageProvider = languageMapper,
    tokenizationPolicy = TokenizationPolicy { text, lexer, cancellation ->
      performLexingImpl(text, lexer, cancellation, logger)
    },
    lexer = lexer,
    builderFactory = SyntaxBuilderFactory { text, tokens, startLexeme ->
      createBuilder(text, tokens, startLexeme)
    },
    extensions = ::ExtensionSupport
  )
}

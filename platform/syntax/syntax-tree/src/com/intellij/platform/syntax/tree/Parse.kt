// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.tree

import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.LanguageSyntaxDefinition
import com.intellij.platform.syntax.Logger
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.extensions.currentExtensionSupport
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.lexer.buildTokenList
import com.intellij.platform.syntax.lexer.performLexing
import com.intellij.platform.syntax.parser.DefaultWhitespaceBindingPolicy
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory
import com.intellij.platform.syntax.parser.WhitespaceOrCommentBindingPolicy
import com.intellij.platform.syntax.util.language.SyntaxElementLanguageProvider
import org.jetbrains.annotations.ApiStatus

fun parse(
  text: CharSequence,
  syntaxDefinition: LanguageSyntaxDefinition,
  languageMapper: SyntaxElementLanguageProvider,
  cancellationProvider: CancellationProvider? = null,
  logger: Logger? = null,
): KmpSyntaxNode {
  return parse(
    text = text,
    lexerFactory = syntaxDefinition::createLexer,
    parser = syntaxDefinition::parse,
    whitespaces = syntaxDefinition.whitespaces,
    comments = syntaxDefinition.comments,
    languageMapper = languageMapper,
    cancellationProvider = cancellationProvider,
    logger = logger,
    whitespaceOrCommentBindingPolicy = syntaxDefinition.whitespaceOrCommentBindingPolicy,
  )
}

fun parse(
  text: CharSequence,
  lexerFactory: () -> Lexer,
  parser: (SyntaxTreeBuilder) -> Unit,
  whitespaces: SyntaxElementTypeSet,
  comments: SyntaxElementTypeSet,
  languageMapper: SyntaxElementLanguageProvider,
  cancellationProvider: CancellationProvider? = null,
  logger: Logger? = null,
  tokenizationPolicy: TokenizationPolicy = defaultTokenizationPolicy(logger),
  whitespaceOrCommentBindingPolicy: WhitespaceOrCommentBindingPolicy? = DefaultWhitespaceBindingPolicy,
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
  ).withWhitespaceOrCommentBindingPolicy(whitespaceOrCommentBindingPolicy)
    .withStartOffset(startLexemeOffset)
    .build()

  val lexer = lexerFactory()
  val tokens = tokenizationPolicy.tokenize(text, lexer, cancellationProvider)
  val builder = createBuilder(text, tokens)
  parser(builder)
  val markers = builder.toAstMarkers()
  return KmpSyntaxNode.root(
    text,
    markers,
    tokens = builder.tokens,
    languageProvider = languageMapper,
    tokenizationPolicy = tokenizationPolicy,
    lexer = lexer,
    builderFactory = SyntaxBuilderFactory { text, tokens, startLexeme ->
      createBuilder(text, tokens, startLexeme)
    },
    extensions = ::currentExtensionSupport
  )
}

fun defaultTokenizationPolicy(logger: Logger?): TokenizationPolicy = TokenizationPolicy { text, lexer, cancellation ->
  performLexing(text, lexer, cancellation, logger)
}

fun fleetTokenizationPolicy(logger: Logger?): TokenizationPolicy = TokenizationPolicy { text, lexer, cancellation ->
  val result = performLexing(text, lexer, cancellation, logger)
  val isEmpty = result.tokenCount == 0
  when (isEmpty) {
    true -> buildTokenList { token("", SyntaxTokenTypes.WHITE_SPACE) }
    false -> result
  }
}

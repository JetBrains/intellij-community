// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.parser

import com.intellij.platform.syntax.*
import com.intellij.platform.syntax.impl.builder.ParsingTreeBuilder
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.logger.noopLogger
import com.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory.Builder
import org.jetbrains.annotations.ApiStatus

/**
 * Factory for constructing a [SyntaxTreeBuilder]
 */
@ApiStatus.Experimental
object SyntaxTreeBuilderFactory {
  @JvmStatic
  fun builder(
    text: CharSequence,
    whitespaces: Set<SyntaxElementType>,
    comments: Set<SyntaxElementType>,
    lexer: Lexer,
  ): Builder = BuilderImpl(lexer, text, whitespaces, comments)

  interface Builder {
    /**
     * Use this property to notify the builder that you are parsing just a part of a file starting from [startOffset].
     */
    fun withStartOffset(startOffset: Int): Builder

    /**
     * You can be notified about every skipped whitespace with this hook
     */
    fun withWhitespaceSkippedCallback(callback: WhitespaceSkippedCallback): Builder

    /**
     * Avoid lexing the text if you already have a lexing result
     */
    fun withCachedLexemes(cachedLexemes: TokenList?): Builder

    /**
     * in debug mode, some additional checks are performed
     */
    fun withDebugMode(debugMode: Boolean): Builder

    /**
     * This language is used for debugging purposes
     */
    fun withLanguage(language: String?): Builder

    /**
     * [cancellationProvider] is called once in a while to check if the parsing should be stopped
     */
    fun withCancellationProvider(cancellationProvider: CancellationProvider?): Builder

    /**
     * Binding policy for whitespaces and comments
     * TODO add better description
     */
    fun withWhitespaceOrCommentBindingPolicy(policy: WhitespaceOrCommentBindingPolicy?): Builder

    /**
     * Install an [OpaqueElementPolicy] for this builder.
     */
    fun withOpaquePolicy(policy: OpaqueElementPolicy?): Builder

    /**
     * Install a logger to get notified about errors
     */
    fun withLogger(logger: Logger?): Builder

    /**
     * Builds a [SyntaxTreeBuilder] with all installed properties
     */
    fun build(): SyntaxTreeBuilder
  }
}

private class BuilderImpl(
  private val lexer: Lexer,
  private val text: CharSequence,
  private val whitespaces: Set<SyntaxElementType>,
  private val comments: Set<SyntaxElementType>,
) : Builder {
  private var startOffset: Int = 0
  private var whitespaceSkippedCallback: WhitespaceSkippedCallback? = null
  private var cachedLexemes: TokenList? = null
  private var debugMode: Boolean = false
  private var language: String? = null
  private var cancellationProvider: CancellationProvider? = null
  private var logger: Logger? = null
  private var whitespaceOrCommentBindingPolicy: WhitespaceOrCommentBindingPolicy? = null
  private var opaquePolicy: OpaqueElementPolicy? = null
  
  override fun withStartOffset(startOffset: Int): Builder {
    this.startOffset = startOffset
    return this
  }

  override fun withWhitespaceSkippedCallback(callback: WhitespaceSkippedCallback): Builder {
    this.whitespaceSkippedCallback = callback
    return this
  }

  override fun withCachedLexemes(cachedLexemes: TokenList?): Builder {
    this.cachedLexemes = cachedLexemes
    return this
  }

  override fun withDebugMode(debugMode: Boolean): Builder {
    this.debugMode = debugMode
    return this
  }

  override fun withLanguage(language: String?): Builder {
    this.language = language
    return this
  }

  override fun withCancellationProvider(cancellationProvider: CancellationProvider?): Builder {
    this.cancellationProvider = cancellationProvider
    return this
  }

  override fun withWhitespaceOrCommentBindingPolicy(policy: WhitespaceOrCommentBindingPolicy?): Builder {
    this.whitespaceOrCommentBindingPolicy = policy
    return this
  }

  override fun withOpaquePolicy(policy: OpaqueElementPolicy?): Builder {
    this.opaquePolicy = policy
    return this
  }

  override fun withLogger(logger: Logger?): Builder {
    this.logger = logger
    return this
  }

  override fun build(): SyntaxTreeBuilder {
    val builder = ParsingTreeBuilder(
      lexer = lexer,
      text = text,
      myWhitespaces = whitespaces.asSyntaxElementTypeSet(),
      myComments = comments.asSyntaxElementTypeSet(),
      startOffset = startOffset,
      myWhitespaceSkippedCallback = whitespaceSkippedCallback,
      cachedLexemes = cachedLexemes,
      myDebugMode = debugMode,
      language = language,
      cancellationProvider = null,
      logger = logger ?: noopLogger(),
      whitespaceOrCommentBindingPolicy = whitespaceOrCommentBindingPolicy ?: WhitespaceOrCommentBindingPolicy { false },
      opaquePolicy = opaquePolicy ?: OpaqueElementPolicy { null },
    )
    return builder as SyntaxTreeBuilder
  }
}

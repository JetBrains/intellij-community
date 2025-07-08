// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax

import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.parser.ProductionResult
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.tree.SyntaxNode
import org.jetbrains.annotations.ApiStatus

/**
 * A parser that is attached to chameleon (lazy parseable) nodes which allows to parse them lazily on demand.
 * This parser also allows reparsing the node incrementally.
 *
 * It is guaranteed that the passed node has the element type corresponding to the lazy parser.
 *
 * ### Implementation note:
 *
 * Provided [SyntaxNode] might be backed by different tree implementations,
 * depending on the syntax-lib client. So please don't make any assumptions on the actual types of the passed instances.
 *
 * If you want to add platform-specific code, introduce an extension point, see [com.intellij.platform.syntax.extensions.ExtensionSupport].
 *
 * @see parseLazyNode
 * @see canBeReparsedIncrementally
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
fun interface LazyParser {
  /**
   * Called when the node is requested to be parsed.
   *
   * @return the result of the parsing operation
   */
  fun parse(parsingContext: LazyParsingContext): ProductionResult

  /**
   * Called when the node is requested to be reparsed incrementally.
   * The provided [parsingContext] contains a token list corresponding to the new node's text.
   * The method should decide if the new token list can be used as a valid input for the parser.
   *
   * An example:
   * This lazy parser corresponds to a code-block in Java. The method should check that the provided token list
   * represents a valid brace structure, i.e. the braces are matched and there are no extra tokens before the opening brace and
   * after the closing brace.
   *
   * @return true if the new token list can be used as a valid input for the parser, false otherwise.
   */
  fun canBeReparsedIncrementally(parsingContext: LazyParsingContext): Boolean = false

  /**
   * Creates a lexer for the given node.
   */
  fun createLexer(lexingContext: LazyLexingContext): Lexer? = null
}

/**
 * Parses the given node and returns [ProductionResult].
 *
 * @see LazyParser.parse
 */
@ApiStatus.Experimental
fun parseLazyNode(parsingContext: LazyParsingContext): ProductionResult {
  return parsingContext.lazyParser.parse(parsingContext)
}

/**
 * Checks if the given node can be reparsed incrementally.
 *
 * @see LazyParser.canBeReparsedIncrementally
 */
@ApiStatus.Experimental
fun canLazyNodeBeReparsedIncrementally(parsingContext: LazyParsingContext): Boolean {
  return parsingContext.lazyParser.canBeReparsedIncrementally(parsingContext)
}

/**
 * @param node the node being parsed
 * @param tokenList the token list being parsed. Might be missing if the parsing engine does not store this information.
 * @param syntaxTreeBuilder a syntax tree builder for the node to be parsed
 * @param cancellationProvider a cancellation provider for the parser
 */
@ApiStatus.Experimental
class LazyParsingContext(
  val node: SyntaxNode,
  val tokenList: TokenList,
  val syntaxTreeBuilder: SyntaxTreeBuilder,
  val cancellationProvider: CancellationProvider,
) {
  /**
   * text of the node to be reparsed
   */
  val text: CharSequence get() = node.text

  /**
   * parser for the node to be reparsed
   */
  internal val lazyParser: LazyParser
    get() = node.type.lazyParser ?: error("Node ${node} has non-lazy element type ${node.type}")
}

/**
 * @param node the node being lexed
 * @param cancellationProvider a cancellation provider for the lexer
 */
@ApiStatus.Experimental
class LazyLexingContext(
  val node: SyntaxNode,
  val cancellationProvider: CancellationProvider,
)

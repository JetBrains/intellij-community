// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax

import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.parser.ProductionResult
import com.intellij.platform.syntax.tree.SyntaxNode
import com.intellij.platform.syntax.tree.SyntaxTree
import org.jetbrains.annotations.ApiStatus

/**
 * A parser that is attached to so-called chameleon nodes which allows to parse them lazily on demand.
 *
 * ### Implementation note:
 *
 * Provided [SyntaxTree] and [SyntaxNode] might be backed by different tree implementations,
 * depending on the syntax-lib client. So please don't make any assumptions on the actual types of the passed instances.
 *
 * If you want to add platform-specific code, introduce an extension point, see [com.intellij.platform.syntax.extensions.ExtensionSupport].
 *
 * @see parseLazyNode
 * @see tryReparseLazyNode
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
interface LazyParser {
  /**
   * Called when the node is requested to be parsed.
   *
   * @return the result of the parsing operation
   */
  fun parse(parsingContext: LazyParsingContext): ProductionResult

  /**
   * Called when the node is requested to be reparsed.
   *
   * @return the result of the parsing operation or `null` if reparsing is not possible
   *         (e.g., when braces got unbalanced in the next)
   */
  fun tryReparse(parsingContext: LazyParsingContext): ProductionResult? = null
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
 * Tries to reparse the given node and returns [ProductionResult] if possible.
 *
 * @see LazyParser.tryReparse
 */
@ApiStatus.Experimental
fun tryReparseLazyNode(parsingContext: LazyParsingContext): ProductionResult? {
  return parsingContext.lazyParser.tryReparse(parsingContext)
}

/**
 * @param tree the tree being parsed
 * @param node the node being parsed
 * @param text the text of the node being parsed
 * @param tokenList the token list being parsed. Might be missing if the parsing engine does not store this information.
 */
@ApiStatus.Experimental
class LazyParsingContext(
  val tree: SyntaxTree,
  val node: SyntaxNode,
  val text: CharSequence,
  val tokenList: TokenList?,
) {
  internal val lazyParser: LazyParser
    get() = node.type.lazyParser ?: error("Node ${node} has non-lazy element type ${node.type}")
}
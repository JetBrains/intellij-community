// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
@file:Suppress("FunctionName")

package com.intellij.platform.syntax.util.runtime

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.util.runtime.impl.SyntaxGeneratedParserRuntimeImpl
import org.jetbrains.annotations.ApiStatus

/**
 * SyntaxGeneratedParserRuntime interface defines all the necessary methods for generated parsers to work.
 * The class is not expected to be used manually.
 *
 * @see SyntaxGeneratedParserRuntime() factory method.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface SyntaxGeneratedParserRuntime {
  val syntaxBuilder: SyntaxTreeBuilder
  val parserUserState: ParserUserState?
  val maxRecursionDepth: Int

  fun init(
    parse: (SyntaxElementType, SyntaxGeneratedParserRuntime) -> Unit,
    extendsSets: Array<SyntaxElementTypeSet> = emptyArray(),
  )

  fun advanceToken(level: Int): Boolean
  fun eof(level: Int): Boolean
  fun current_position_(): Int
  fun isWhitespaceOrComment(type: SyntaxElementType?): Boolean
  fun recursion_guard_(level: Int, funcName: String): Boolean
  fun empty_element_parsed_guard_(funcName: String, pos: Int): Boolean
  fun invalid_left_marker_guard_(marker: SyntaxTreeBuilder.Marker?, funcName: String?): Boolean
  fun leftMarkerIs(type: SyntaxElementType?): Boolean

  fun consumeTokens(pin: Int, vararg token: SyntaxElementType?): Boolean
  fun consumeTokensSmart(pin: Int, vararg token: SyntaxElementType?): Boolean
  fun consumeTokenSmart(token: SyntaxElementType): Boolean
  fun consumeTokenSmart(token: String): Boolean
  fun consumeToken(token: SyntaxElementType): Boolean
  fun consumeTokenFast(token: SyntaxElementType): Boolean
  fun consumeToken(text: String, caseSensitive: Boolean = (this as SyntaxGeneratedParserRuntimeImpl).isLanguageCaseSensitive): Boolean
  fun consumeTokenFast(text: String): Boolean
  fun consumeToken(vararg tokens: SyntaxElementType): Boolean
  fun consumeTokenSmart(vararg tokens: SyntaxElementType): Boolean
  fun consumeTokenFast(vararg tokens: SyntaxElementType): Boolean

  fun parseTokens(pin: Int, vararg tokens: SyntaxElementType?): Boolean
  fun parseTokens(smart: Boolean, pin: Int, vararg tokens: SyntaxElementType?): Boolean
  fun parseTokensSmart(pin: Int, vararg tokens: SyntaxElementType?): Boolean

  fun nextTokenIsFast(token: SyntaxElementType?): Boolean
  fun nextTokenIsFast(vararg tokens: SyntaxElementType): Boolean
  fun nextTokenIsFast(tokens: SyntaxElementTypeSet): Boolean
  fun nextTokenIsSmart(token: SyntaxElementType?): Boolean
  fun nextTokenIsSmart(vararg tokens: SyntaxElementType): Boolean
  fun nextTokenIs(frameName: String?, vararg tokens: SyntaxElementType): Boolean
  fun nextTokenIsSlow(frameName: String?, vararg tokens: SyntaxElementType): Boolean
  fun nextTokenIs(token: SyntaxElementType): Boolean
  fun nextTokenIs(tokenText: String): Boolean
  fun nextTokenIsFast(tokenText: String): Boolean
  fun nextTokenIsFast(tokenText: String, caseSensitive: Boolean): Int

  fun addVariant(text: String)

  fun enter_section_(): SyntaxTreeBuilder.Marker
  fun enter_section_(level: Int, modifiers: Modifiers, frameName: String?): SyntaxTreeBuilder.Marker
  fun enter_section_(level: Int, modifiers: Modifiers): SyntaxTreeBuilder.Marker
  fun enter_section_(level: Int, modifiers: Modifiers, elementType: SyntaxElementType?, frameName: String?): SyntaxTreeBuilder.Marker

  fun exit_section_(marker: SyntaxTreeBuilder.Marker?, elementType: SyntaxElementType?, result: Boolean)
  fun exit_section_(level: Int, marker: SyntaxTreeBuilder.Marker?, result: Boolean, pinned: Boolean, eatMore: Parser?)
  fun exit_section_(
    level: Int,
    marker: SyntaxTreeBuilder.Marker?,
    elementType: SyntaxElementType?,
    result: Boolean,
    pinned: Boolean,
    eatMore: Parser?,
  )

  fun <T> register_hook_(hook: Hook<T>, param: T)

  fun report_error_(result: Boolean): Boolean
  fun report_error_(state: ErrorState, advance: Boolean)

  fun parseWithProtectedLastPos(level: Int, parser: Parser): Boolean
  fun parseAsTree(
    state: ErrorState,
    level: Int,
    chunkType: SyntaxElementType,
    checkBraces: Boolean,
    parser: Parser,
    eatMoreConditionParser: Parser,
  ): Boolean
}

@ApiStatus.Experimental
fun create_token_set_(vararg tokenTypes: SyntaxElementType): SyntaxElementTypeSet {
  return syntaxElementTypeSetOf(*tokenTypes)
}
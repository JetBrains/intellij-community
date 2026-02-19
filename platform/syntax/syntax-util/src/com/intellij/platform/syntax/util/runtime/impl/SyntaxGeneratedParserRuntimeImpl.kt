// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("FunctionName")

package com.intellij.platform.syntax.util.runtime.impl

import com.intellij.platform.syntax.Logger
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.WhitespacesBinders
import com.intellij.platform.syntax.util.runtime.BracePair
import com.intellij.platform.syntax.util.runtime.DUMMY_BLOCK
import com.intellij.platform.syntax.util.runtime.ErrorState
import com.intellij.platform.syntax.util.runtime.Frame
import com.intellij.platform.syntax.util.runtime.Hook
import com.intellij.platform.syntax.util.runtime.Modifiers
import com.intellij.platform.syntax.util.runtime.Modifiers.Companion._AND_
import com.intellij.platform.syntax.util.runtime.Modifiers.Companion._COLLAPSE_
import com.intellij.platform.syntax.util.runtime.Modifiers.Companion._LEFT_
import com.intellij.platform.syntax.util.runtime.Modifiers.Companion._LEFT_INNER_
import com.intellij.platform.syntax.util.runtime.Modifiers.Companion._NONE_
import com.intellij.platform.syntax.util.runtime.Modifiers.Companion._NOT_
import com.intellij.platform.syntax.util.runtime.Modifiers.Companion._UPPER_
import com.intellij.platform.syntax.util.runtime.Parser
import com.intellij.platform.syntax.util.runtime.ParserUserState
import com.intellij.platform.syntax.util.runtime.SyntaxGeneratedParserRuntime
import com.intellij.platform.syntax.util.runtime.SyntaxRuntimeBundle
import com.intellij.platform.syntax.util.runtime.TOKEN_ADVANCER
import org.jetbrains.annotations.Contract
import kotlin.math.min

internal class SyntaxGeneratedParserRuntimeImpl(
  override val syntaxBuilder: SyntaxTreeBuilder,
  override val maxRecursionDepth: Int,
  internal val isLanguageCaseSensitive: Boolean,
  internal val braces: Collection<BracePair>,
  internal val logger: Logger,
  override val parserUserState: ParserUserState?,
) : SyntaxGeneratedParserRuntime {
  internal val errorState: ErrorStateImpl = ErrorStateImpl()

  internal lateinit var parser: (SyntaxElementType, SyntaxGeneratedParserRuntime) -> Unit

  override fun init(
    parse: (SyntaxElementType, SyntaxGeneratedParserRuntime) -> Unit,
    extendsSets: Array<SyntaxElementTypeSet>,
  ) {
    parser = parse
    errorState.initState(this, extendsSets)
  }

  override fun advanceToken(level: Int): Boolean {
    if (syntaxBuilder.eof()) return false
    syntaxBuilder.advanceLexer()
    return true
  }

  override fun eof(level: Int): Boolean {
    return syntaxBuilder.eof()
  }

  override fun current_position_(): Int {
    return syntaxBuilder.rawTokenIndex()
  }

  override fun recursion_guard_(level: Int, funcName: String): Boolean {
    if (level > maxRecursionDepth) {
      syntaxBuilder.mark().error(SyntaxRuntimeBundle.message("parsing.error.maximum.recursion.level.reached.in", maxRecursionDepth, funcName))
      return false
    }
    return true
  }

  override fun empty_element_parsed_guard_(funcName: String, pos: Int): Boolean {
    if (pos == current_position_()) {
      // sometimes this is a correct situation, therefore no explicit marker
      syntaxBuilder.error(SyntaxRuntimeBundle.message("parsing.error.empty.element.parsed.in.at.offset", funcName, syntaxBuilder.currentOffset))
      return false
    }
    return true
  }

  override fun invalid_left_marker_guard_(marker: SyntaxTreeBuilder.Marker?, funcName: String?): Boolean {
    val goodMarker = marker != null
    if (!goodMarker) return false
    return errorState.currentFrame != null
  }

  override fun leftMarkerIs(type: SyntaxElementType?): Boolean {
    val lastDoneMarker = syntaxBuilder.lastDoneMarker
    return lastDoneMarker?.getNodeType() === type
  }

  private fun consumeTokens(smart: Boolean, pin: Int, vararg tokens: SyntaxElementType?): Boolean {
    var result = true
    var pinned = false
    var i = 0
    val tokensLength = tokens.size
    while (i < tokensLength) {
      if (pin > 0 && i == pin) pinned = result
      if (result || pinned) {
        val fast = smart && i == 0
        tokens[i]?.let {
          if (!(if (fast) consumeTokenFast(it) else consumeToken(it))) {
            result = false
            if (pin < 0 || pinned) report_error_(errorState, false)
          }
        }
      }
      i++
    }
    return pinned || result
  }

  override fun consumeTokens(pin: Int, vararg token: SyntaxElementType?): Boolean {
    return consumeTokens(false, pin, *token)
  }

  override fun consumeTokensSmart(pin: Int, vararg token: SyntaxElementType?): Boolean {
    return consumeTokens(true, pin, *token)
  }

  override fun parseTokens(pin: Int, vararg tokens: SyntaxElementType?): Boolean {
    return parseTokens(false, pin, *tokens)
  }

  override fun parseTokensSmart(pin: Int, vararg tokens: SyntaxElementType?): Boolean {
    return parseTokens(true, pin, *tokens)
  }

  override fun parseTokens(smart: Boolean, pin: Int, vararg tokens: SyntaxElementType?): Boolean {
    val marker: SyntaxTreeBuilder.Marker = syntaxBuilder.mark()
    val result: Boolean = consumeTokens(smart, pin, *tokens)
    if (!result) {
      marker.rollbackTo()
    }
    else {
      marker.drop()
    }
    return result
  }

  override fun consumeTokenSmart(token: SyntaxElementType): Boolean {
    return consumeTokenFast(token)
  }

  override fun consumeTokenSmart(token: String): Boolean {
    return consumeTokenFast(token)
  }

  @Contract(mutates = "param1")
  override fun consumeToken(token: SyntaxElementType): Boolean {
    addVariantSmart(token, true)
    return consumeTokenFast(token)
  }

  override fun consumeTokenFast(token: SyntaxElementType): Boolean {
    if (nextTokenIsFast(token)) {
      syntaxBuilder.advanceLexer()
      return true
    }
    return false
  }

  override fun consumeToken(text: String, caseSensitive: Boolean): Boolean {
    addVariantSmart(text, true)
    var count: Int = nextTokenIsFast(text, caseSensitive)
    if (count > 0) {
      while (count-- > 0) syntaxBuilder.advanceLexer()
      return true
    }
    return false
  }

  override fun consumeTokenFast(text: String): Boolean {
    var count: Int = nextTokenIsFast(text, isLanguageCaseSensitive)
    if (count > 0) {
      while (count-- > 0) syntaxBuilder.advanceLexer()
      return true
    }
    return false
  }

  override fun consumeToken(vararg tokens: SyntaxElementType): Boolean {
    addVariantSmart(tokens, true)
    return consumeTokenFast(*tokens)
  }

  override fun consumeTokenSmart(vararg tokens: SyntaxElementType): Boolean {
    return consumeTokenFast(*tokens)
  }

  override fun consumeTokenFast(vararg tokens: SyntaxElementType): Boolean {
    if (nextTokenIsFast(*tokens)) {
      syntaxBuilder.advanceLexer()
      return true
    }
    return false
  }

  override fun nextTokenIsFast(token: SyntaxElementType?): Boolean {
    return syntaxBuilder.tokenType === token
  }

  override fun nextTokenIsFast(vararg tokens: SyntaxElementType): Boolean {
    val tokenType: SyntaxElementType? = syntaxBuilder.tokenType
    return tokens.indexOf(tokenType) >= 0
  }

  override fun nextTokenIsFast(tokens: SyntaxElementTypeSet): Boolean {
    return tokens.contains(syntaxBuilder.tokenType)
  }

  override fun nextTokenIsSmart(token: SyntaxElementType?): Boolean {
    return nextTokenIsFast(token)
  }

  override fun nextTokenIsSmart(vararg tokens: SyntaxElementType): Boolean {
    return nextTokenIsFast(*tokens)
  }

  override fun nextTokenIs(frameName: String?, vararg tokens: SyntaxElementType): Boolean {
    val track = !errorState.suppressErrors && errorState.predicateCount < 2 && errorState.predicateSign
    return if (!track) nextTokenIsFast(*tokens) else nextTokenIsSlow(frameName, *tokens)
  }

  override fun nextTokenIsSlow(frameName: String?, vararg tokens: SyntaxElementType): Boolean {
    val tokenType: SyntaxElementType? = syntaxBuilder.tokenType
    if (!frameName.isNullOrEmpty()) {
      addVariantInner(errorState, errorState.currentFrame, syntaxBuilder.rawTokenIndex(), frameName)
    }
    else {
      for (token in tokens) {
        addVariant(errorState, token)
      }
    }
    if (tokenType == null) return false
    return tokens.indexOf(tokenType) != -1
  }

  override fun nextTokenIs(token: SyntaxElementType): Boolean {
    if (!addVariantSmart(token, false)) return true
    return nextTokenIsFast(token)
  }

  override fun nextTokenIs(tokenText: String): Boolean {
    if (!addVariantSmart(tokenText, false)) return true
    return nextTokenIsFast(tokenText, isLanguageCaseSensitive) > 0
  }

  override fun nextTokenIsFast(tokenText: String): Boolean {
    return nextTokenIsFast(tokenText, isLanguageCaseSensitive) > 0
  }

  override fun nextTokenIsFast(tokenText: String, caseSensitive: Boolean): Int {
    val sequence: CharSequence = syntaxBuilder.text
    val offset: Int = syntaxBuilder.currentOffset
    val endOffset = offset + tokenText.length
    val subSequence = sequence.subSequence(offset, min(endOffset.toDouble(), sequence.length.toDouble()).toInt())
    if (!subSequence.contentEquals(tokenText, caseSensitive)) return 0

    var count = 0
    while (true) {
      val nextOffset: Int = syntaxBuilder.rawTokenTypeStart(++count)
      if (nextOffset > endOffset) {
        return -count
      }
      else if (nextOffset == endOffset) {
        break
      }
    }
    return count
  }

  @Suppress("unused")
  private fun addVariantSmart(token: Any, force: Boolean): Boolean {
    syntaxBuilder.eof()
    if (!errorState.suppressErrors && errorState.predicateCount < 2) {
      addVariant(errorState, token)
    }
    return true
  }

  override fun addVariant(text: String) {
    addVariant(errorState, text)
  }

  private fun addVariant(
    state: ErrorStateImpl,
    o: Any,
  ) {
    syntaxBuilder.eof() // skip whitespaces
    addVariantInner(state, state.currentFrame, syntaxBuilder.rawTokenIndex(), o)
  }

  private fun addVariantInner(
    state: ErrorStateImpl,
    frame: FrameImpl?,
    pos: Int,
    o: Any?,
  ) {
    val variant: Variant = state.variantPool.alloc().init(pos, o)
    if (state.predicateSign) {
      state.variants.add(variant)
      if (frame != null && frame.lastVariantAt < pos) {
        frame.lastVariantAt = pos
      }
    }
    else {
      state.unexpected.add(variant)
    }
  }

  private fun wasAutoSkipped(steps: Int): Boolean {
    for (i in -1 downTo -steps) {
      if (!isWhitespaceOrComment(syntaxBuilder.rawLookup(i))) return false
    }
    return true
  }

  // simple enter/exit methods pair that doesn't require a frame object
  override fun enter_section_(): SyntaxTreeBuilder.Marker {
    reportFrameError(errorState)
    errorState.level++
    return syntaxBuilder.mark()
  }

  override fun exit_section_(
    marker: SyntaxTreeBuilder.Marker?,
    elementType: SyntaxElementType?,
    result: Boolean,
  ) {
    close_marker_impl_(errorState.currentFrame, marker, elementType, result)
    run_hooks_impl_(errorState, if (result) elementType else null)
    errorState.level--
  }

  // complex enter/exit methods pair with a frame object
  override fun enter_section_(level: Int, modifiers: Modifiers, frameName: String?): SyntaxTreeBuilder.Marker {
    return enter_section_(level, modifiers, null, frameName)
  }

  override fun enter_section_(level: Int, modifiers: Modifiers): SyntaxTreeBuilder.Marker = enter_section_(level, modifiers, null, null)

  override fun enter_section_(level: Int, modifiers: Modifiers, elementType: SyntaxElementType?, frameName: String?): SyntaxTreeBuilder.Marker {
    reportFrameError(errorState)
    val marker: SyntaxTreeBuilder.Marker = syntaxBuilder.mark()
    enter_section_impl_(level, modifiers, elementType, frameName)
    return marker
  }

  private fun enter_section_impl_(level: Int, modifiers: Modifiers, elementType: SyntaxElementType?, frameName: String?) {
    errorState.level++
    val frame: FrameImpl = errorState.framePool.alloc().init(
      syntaxBuilder, errorState, level, modifiers, elementType, frameName
    )
    if (((frame.modifiers and _LEFT_) or (frame.modifiers and _LEFT_INNER_)) != _NONE_) {
      val left: SyntaxTreeBuilder.Marker? = syntaxBuilder.lastDoneMarker
      if (invalid_left_marker_guard_(left, frameName)) {
        frame.leftMarker = left
      }
    }
    errorState.currentFrame = frame
    if ((modifiers and _AND_) != _NONE_) {
      if (errorState.predicateCount == 0 && !errorState.predicateSign) {
        throw AssertionError("Incorrect false predicate sign")
      }
      errorState.predicateCount++
    }
    else if ((modifiers and _NOT_) != _NONE_) {
      errorState.predicateSign = errorState.predicateCount != 0 && !errorState.predicateSign
      errorState.predicateCount++
    }
  }

  override fun exit_section_(
    level: Int,
    marker: SyntaxTreeBuilder.Marker?,
    result: Boolean,
    pinned: Boolean,
    eatMore: Parser?,
  ) {
    exit_section_(level, marker, null, result, pinned, eatMore)
  }

  override fun exit_section_(
    level: Int,
    marker: SyntaxTreeBuilder.Marker?,
    elementType: SyntaxElementType?,
    result: Boolean,
    pinned: Boolean,
    eatMore: Parser?,
  ) {
    val frame = errorState.currentFrame
    errorState.currentFrame = frame?.parentFrame
    val elementTypeToExit = frame?.elementType ?: elementType
    if (frame == null || level != frame.level) {
      logger.error("Unbalanced error section: got $frame, expected level $level")
      if (frame != null) errorState.framePool.recycle(frame)
      close_marker_impl_(frame, marker, elementTypeToExit, result)
      return
    }

    close_frame_impl_(errorState, frame, marker, elementTypeToExit, result, pinned)
    exit_section_impl_(errorState, frame, elementTypeToExit, result, pinned, eatMore)
    run_hooks_impl_(errorState, if (pinned || result) elementTypeToExit else null)
    errorState.framePool.recycle(frame)
    errorState.level--
  }

  override fun <T> register_hook_(hook: Hook<T>, param: T) {
    errorState.hooks.addLast(HookBatch(hook, param, errorState.level))
  }

  private fun run_hooks_impl_(state: ErrorStateImpl, elementType: SyntaxElementType?) {
    val hooks = state.hooks
    if (hooks.isEmpty()) return

    var marker = if (elementType == null) null else syntaxBuilder.lastDoneMarker

    if (elementType != null && marker == null) {
      syntaxBuilder.mark().error(SyntaxRuntimeBundle.message("parsing.error.no.expected.done.marker.at.offset", syntaxBuilder.currentOffset))
    }

    do {
      val curHookBatch = hooks.lastOrNull()?.takeIf { it.level >= state.level } ?: break
      if (curHookBatch.level == state.level) {
        marker = curHookBatch.process(this, marker)
      }
      hooks.removeLast()
    }
    while (true)
  }

  private fun exit_section_impl_(
    state: ErrorStateImpl,
    frame: FrameImpl,
    elementType: SyntaxElementType?,
    result: Boolean,
    pinned: Boolean,
    eatMoreParser: Parser?,
  ) {
    val initialPos: Int = syntaxBuilder.rawTokenIndex()
    replace_variants_with_name_(state, frame, elementType, result, pinned)
    val lastErrorPos = if (frame.lastVariantAt < 0) initialPos else frame.lastVariantAt
    if (!state.suppressErrors && eatMoreParser != null) {
      state.suppressErrors = true
      val eatMoreFlagOnce = !syntaxBuilder.eof() && eatMoreParser.parse(this, frame.level + 1)
      var eatMoreFlag = eatMoreFlagOnce || !result && frame.position == initialPos && lastErrorPos > frame.position

      val latestDoneMarker: SyntaxTreeBuilder.Marker? =
        if ((pinned || result) && (state.altMode || elementType != null) &&
            eatMoreFlagOnce) getLatestExtensibleDoneMarker(syntaxBuilder)
        else null
      // advance to the last error pos
      // skip tokens until lastErrorPos. parseAsTree might look better here...
      var parenCount = 0
      while ((eatMoreFlag || parenCount > 0) && syntaxBuilder.rawTokenIndex() < lastErrorPos) {
        val tokenType: SyntaxElementType? = syntaxBuilder.tokenType
        if (state.braces.isNotEmpty()) {
          if (tokenType === state.braces[0].leftBrace) parenCount++
          else if (tokenType === state.braces[0].rightBrace) parenCount--
        }
        if (syntaxBuilder.rawTokenIndex() >= lastErrorPos) break
        TOKEN_ADVANCER.parse(this, frame.level + 1)
        eatMoreFlag = eatMoreParser.parse(this, frame.level + 1)
      }
      var errorReported = frame.errorReportedAt == initialPos || !result && frame.errorReportedAt >= frame.position
      if (errorReported || eatMoreFlag) {
        if (!errorReported) {
          errorReported = reportError(state, frame, false, true, true)
        }
        else if (eatMoreFlag) {
          TOKEN_ADVANCER.parse(this, frame.level + 1)
        }
        if (eatMoreParser.parse(this, frame.level + 1)) {
          parseAsTree(state, frame.level + 1, DUMMY_BLOCK, true, TOKEN_ADVANCER, eatMoreParser)
        }
      }
      else if (eatMoreFlagOnce || !result && frame.position != syntaxBuilder.rawTokenIndex() || frame.errorReportedAt > initialPos) {
        errorReported = reportError(state, frame, false, true, false)
      }
      else if (!result && pinned && frame.errorReportedAt < 0) {
        errorReported = reportError(state, frame, elementType != null, false, false)
      }
      // whitespace prefix makes the very first frame offset bigger than the marker start offset which is always 0
      if (latestDoneMarker != null && frame.position >= latestDoneMarker.getStartTokenIndex() && frame.position <= latestDoneMarker.getEndTokenIndex()) {
        extend_marker_impl(latestDoneMarker)
      }
      state.suppressErrors = false
      if (errorReported || result) {
        state.clearVariants(true, 0)
        state.clearVariants(false, 0)
        frame.lastVariantAt = -1
        var f: FrameImpl? = frame
        while (f != null && f.variantCount > 0) {
          f.variantCount = 0
          f = f.parentFrame
        }
      }
    }
    else if (!result && pinned && frame.errorReportedAt < 0) {
      // do not report if there are errors beyond the current position
      if (lastErrorPos == initialPos) {
        // do not force, inner recoverRoot might have skipped some tokens
        reportError(state, frame, elementType != null && (frame.modifiers and _UPPER_) == _NONE_, false, false)
      }
      else if (lastErrorPos > initialPos) {
        // set the error pos here as if it is reported for future reference
        frame.errorReportedAt = lastErrorPos
      }
    }
    // propagate errorReportedAt up the stack to avoid duplicate reporting
    state.currentFrame?.let { currentFrame ->
      if (currentFrame.errorReportedAt < frame.errorReportedAt) {
        currentFrame.errorReportedAt = frame.errorReportedAt
      }
      if (currentFrame.lastVariantAt < frame.lastVariantAt) {
        currentFrame.lastVariantAt = frame.lastVariantAt
      }
    }
  }

  private fun close_frame_impl_(
    state: ErrorStateImpl,
    frame: FrameImpl,
    marker: SyntaxTreeBuilder.Marker?,
    elementType: SyntaxElementType?,
    result: Boolean,
    pinned: Boolean,
  ) {
    var marker: SyntaxTreeBuilder.Marker? = marker
    var elementType: SyntaxElementType? = elementType
    if (((frame.modifiers and _AND_) or (frame.modifiers and _NOT_)) != _NONE_) {
      val resetLastPos = !state.suppressErrors && frame.lastVariantAt < 0 && frame.position < syntaxBuilder.rawTokenIndex()
      close_marker_impl_(frame, marker, null, false)
      state.predicateCount--
      if ((frame.modifiers and _NOT_) != _NONE_) state.predicateSign = !state.predicateSign
      marker = if (elementType != null && marker != null && (result || pinned)) syntaxBuilder.mark() else null
      if (resetLastPos) frame.lastVariantAt = syntaxBuilder.rawTokenIndex()
    }

    if (elementType != null && marker != null) {
      if (result || pinned) {
        if ((frame.modifiers and _COLLAPSE_) != _NONE_) {
          val last: SyntaxTreeBuilder.Marker? = syntaxBuilder.lastDoneMarker
          if (last != null && last.getStartTokenIndex() == frame.position &&
              state.typeExtends(last.getNodeType(), elementType) &&
              wasAutoSkipped(syntaxBuilder.rawTokenIndex() - last.getEndTokenIndex())) {
            elementType = last.getNodeType()
            last.drop()
          }
        }
        if ((frame.modifiers and _UPPER_) != _NONE_) {
          marker.drop()
          var f: Frame? = frame.parentFrame
          while (f != null) {
            if (f.elementType == null) {
              f = f.parentFrame
            }
            else {
              f.elementType = elementType
              break
            }
          }
        }
        else if ((frame.modifiers and _LEFT_INNER_) != _NONE_ && frame.leftMarker != null) {
          marker.done(elementType)
          frame.leftMarker?.let { extend_marker_impl(it) }
        }
        else if ((frame.modifiers and _LEFT_) != _NONE_ && frame.leftMarker != null) {
          marker.drop()
          frame.leftMarker?.precede()?.done(elementType)
        }
        else {
          if (frame.level == 0) syntaxBuilder.eof() // skip whitespaces

          marker.done(elementType)
        }
      }
      else {
        close_marker_impl_(frame, marker, null, false)
      }
    }
    else if (result || pinned) {
      marker?.drop()
      if ((frame.modifiers and _LEFT_INNER_) != _NONE_ && frame.leftMarker != null) {
        extend_marker_impl(frame.leftMarker!!)
      }
    }
    else {
      close_marker_impl_(frame, marker, null, false)
    }
  }

  private fun extend_marker_impl(marker: SyntaxTreeBuilder.Marker) {
    val precede: SyntaxTreeBuilder.Marker = marker.precede()
    val elementType: SyntaxElementType = marker.getNodeType()
    if (elementType === SyntaxTokenTypes.ERROR_ELEMENT) {
      precede.error(marker.getErrorMessage() ?: "")
    }
    else {
      precede.done(elementType)
    }
    marker.drop()
  }

  /**
   * Finalizes or rolls back a parser marker based on the specified result and element type.
   *
   * @param frame The current parser frame, used to manage error reporting and state during parsing.
   * @param marker The marker to be closed, which represents a node or span in the syntax tree.
   * @param elementType The type of syntax element to be associated with the marker if the operation succeeds.
   * @param result Indicates whether the operation succeeded (`true`) or failed (`false`). A successful operation marks the marker as done or drops it, while a failed operation rolls
   *  it back.
   */
  private fun close_marker_impl_(
    frame: FrameImpl?,
    marker: SyntaxTreeBuilder.Marker?,
    elementType: SyntaxElementType?,
    result: Boolean,
  ) {
    if (marker == null) return
    if (result) {
      if (elementType != null) {
        marker.done(elementType)
      }
      else {
        marker.drop()
      }
    }
    else {
      frame?.let {
        val position: Int = marker.getStartTokenIndex()
        if (frame.errorReportedAt > position) {
          frame.errorReportedAt = frame.parentFrame?.errorReportedAt ?: -1
        }
      }
      marker.rollbackTo()
    }
  }

  private fun replace_variants_with_name_(
    state: ErrorStateImpl,
    frame: FrameImpl,
    elementType: SyntaxElementType?,
    result: Boolean,
    pinned: Boolean,
  ) {
    val initialPos: Int = syntaxBuilder.rawTokenIndex()
    val willFail = !result && !pinned
    if (willFail && initialPos == frame.position && frame.lastVariantAt == frame.position && frame.name != null && state.variants.size >= frame.variantCount + (if (elementType == null) 0 else 2)) {
      state.clearVariants(true, frame.variantCount)
      addVariantInner(state, frame, initialPos, frame.name)
    }
  }

  override fun report_error_(result: Boolean): Boolean {
    if (!result) report_error_(errorState, false)
    return result
  }

  override fun report_error_(state: ErrorState, advance: Boolean) {
    state as ErrorStateImpl

    val frame = state.currentFrame
    if (frame == null) {
      logger.error("unbalanced enter/exit section call: got null")
      return
    }
    val position: Int = syntaxBuilder.rawTokenIndex()
    if (frame.errorReportedAt < position && frame.lastVariantAt > -1 && frame.lastVariantAt <= position) {
      reportError(state, frame, false, true, advance)
    }
  }

  private fun getLatestExtensibleDoneMarker(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    val marker: SyntaxTreeBuilder.Production = builder.productions.last()
    if (marker.isCollapsed()) return null
    return marker as? SyntaxTreeBuilder.Marker
  }

  private fun reportError(
    state: ErrorStateImpl,
    frame: FrameImpl,
    inner: Boolean,
    force: Boolean,
    advance: Boolean,
  ): Boolean {
    val position: Int = syntaxBuilder.rawTokenIndex()
    val expected: String = state.getExpected(position, true)
    if (!force && expected.isEmpty() && !advance) return false

    val actual: CharSequence? = syntaxBuilder.tokenText?.trim()
    val message: String
    if (expected.isEmpty()) {
      if (actual.isNullOrEmpty()) {
        message = SyntaxRuntimeBundle.message("parsing.error.unmatched.input")
      }
      else {
        message = SyntaxRuntimeBundle.message("parsing.error.unexpected", actual.crop(MAX_ERROR_TOKEN_TEXT, true))
      }
    }
    else {
      if (actual.isNullOrEmpty()) {
        message = SyntaxRuntimeBundle.message("parsing.error.expected", expected)
      }
      else {
        message = SyntaxRuntimeBundle.message("parsing.error.expected.got", expected, actual.crop(MAX_ERROR_TOKEN_TEXT, true))
      }
    }
    if (advance) {
      val mark: SyntaxTreeBuilder.Marker = syntaxBuilder.mark()
      TOKEN_ADVANCER.parse(this, frame.level + 1)
      mark.error(message)
    }
    else if (inner) {
      val latestDoneMarker: SyntaxTreeBuilder.Marker? = getLatestExtensibleDoneMarker(syntaxBuilder)
      syntaxBuilder.error(message)
      if (latestDoneMarker != null && frame.position >= latestDoneMarker.getStartTokenIndex() && frame.position <= latestDoneMarker.getEndTokenIndex()) {
        extend_marker_impl(latestDoneMarker)
      }
    }
    else {
      syntaxBuilder.error(message)
    }
    syntaxBuilder.eof() // skip whitespaces
    frame.errorReportedAt = syntaxBuilder.rawTokenIndex()
    return true
  }

  private fun reportFrameError(state: ErrorStateImpl) {
    if (state.currentFrame == null || state.suppressErrors) return
    val frame = state.currentFrame
    val pos: Int = syntaxBuilder.rawTokenIndex()
    if (frame != null && frame.errorReportedAt > pos) {
      // report error for previous unsuccessful frame
      val marker: SyntaxTreeBuilder.Marker? = syntaxBuilder.lastDoneMarker
      var endOffset = marker?.getEndTokenIndex() ?: (pos + 1)
      while (endOffset <= pos && isWhitespaceOrComment(syntaxBuilder.rawLookup(endOffset - pos))) endOffset++
      val inner = endOffset == pos
      syntaxBuilder.eof()
      reportError(state, frame, inner, true, false)
    }
  }

  private fun checkSiblings(
    chunkType: SyntaxElementType,
    parens: ArrayDeque<Pair<SyntaxTreeBuilder.Marker, SyntaxTreeBuilder.Marker?>>,
    siblings: ArrayDeque<Pair<SyntaxTreeBuilder.Marker, Int>>,
  ) {
    main@ while (!siblings.isEmpty()) {
      val parenPair = parens.firstOrNull()
      val rating: Int = siblings.first().second
      var count = 0
      for (pair in siblings) {
        if (pair.second != rating || parenPair != null && pair.first === parenPair.second) break@main
        if (++count >= MAX_CHILDREN_IN_TREE) {
          val parentMarker: SyntaxTreeBuilder.Marker = pair.first.precede()
          parentMarker.setCustomEdgeTokenBinders(WhitespacesBinders.greedyLeftBinder(), null)
          while (count-- > 0) {
            siblings.removeFirst()
          }
          parentMarker.done(chunkType)
          siblings.addFirst(Pair(parentMarker, rating + 1))
          continue@main
        }
      }
      break
    }
  }

  //This is a construction used in SQL and Rust parser utils. We extract it here to restrict access to lastVariantAt field in Frame class
  override fun parseWithProtectedLastPos(level: Int, parser: Parser): Boolean {
    val prev = errorState.currentFrame?.lastVariantAt ?: -1
    val result = parser.parse(this, level)
    errorState.currentFrame?.lastVariantAt = prev
    return result
  }

  override fun parseAsTree(
    state: ErrorState,
    level: Int,
    chunkType: SyntaxElementType,
    checkBraces: Boolean,
    parser: Parser,
    eatMoreConditionParser: Parser,
  ): Boolean {
    state as ErrorStateImpl
    val parens: ArrayDeque<Pair<SyntaxTreeBuilder.Marker, SyntaxTreeBuilder.Marker?>> = ArrayDeque(4)
    val siblings: ArrayDeque<Pair<SyntaxTreeBuilder.Marker, Int>> = ArrayDeque()
    var marker: SyntaxTreeBuilder.Marker? = null

    val lBrace: SyntaxElementType? = if (checkBraces && state.braces.isNotEmpty()) state.braces[0].leftBrace else null
    val rBrace: SyntaxElementType? = if (lBrace != null) state.braces[0].rightBrace else null
    var totalCount = 0
    var tokenCount = 0
    if (lBrace != null) {
      var tokenIdx = -1
      while (syntaxBuilder.rawLookup(tokenIdx) === SyntaxTokenTypes.WHITE_SPACE) tokenIdx--
      val doneMarker = if (syntaxBuilder.rawLookup(tokenIdx) === lBrace) syntaxBuilder.lastDoneMarker else null
      if (doneMarker != null && doneMarker.getStartOffset() == syntaxBuilder.rawTokenTypeStart(tokenIdx) && doneMarker.getNodeType() === SyntaxTokenTypes.ERROR_ELEMENT) {
        parens.add(Pair(doneMarker.precede(), null))
      }
    }
    var c: Int = current_position_()
    while (true) {
      val tokenType: SyntaxElementType? = syntaxBuilder.tokenType
      if (lBrace != null && (tokenType === lBrace || tokenType === rBrace && !parens.isEmpty())) {
        if (marker != null) {
          marker.done(chunkType)
          siblings.addFirst(Pair(marker, 1))
          marker = null
          tokenCount = 0
        }
        if (tokenType === lBrace) {
          val prev = siblings.firstOrNull()
          parens.addFirst(Pair(syntaxBuilder.mark(), prev?.first))
        }
        checkSiblings(chunkType, parens, siblings)
        TOKEN_ADVANCER.parse(this, level)
        if (tokenType === rBrace) {
          val pair: Pair<SyntaxTreeBuilder.Marker, SyntaxTreeBuilder.Marker?> = parens.removeFirst()
          pair.first.done(chunkType)
          while (!siblings.isEmpty() && siblings.first().first != pair.second) {
            siblings.removeFirst()
          }
          siblings.addFirst(Pair(pair.first, 1))

          // drop all markers inside parens
          checkSiblings(chunkType, parens, siblings)
        }
      }
      else {
        if (marker == null) {
          marker = syntaxBuilder.mark()
          marker.setCustomEdgeTokenBinders(WhitespacesBinders.greedyLeftBinder(), null)
        }
        val result = (!parens.isEmpty() || eatMoreConditionParser.parse(this, level + 1)) &&
                     parser.parse(this, level + 1)
        if (result) {
          tokenCount++
          totalCount++
        }
        else {
          break
        }
      }

      if (tokenCount >= MAX_CHILDREN_IN_TREE) {
        marker?.let {
          marker.done(chunkType)
          siblings.addFirst(Pair(marker, 1))
        }
        checkSiblings(chunkType, parens, siblings)
        marker = null
        tokenCount = 0
      }
      if (!empty_element_parsed_guard_("parseAsTree", c)) break
      c = current_position_()
    }
    marker?.drop()
    for (pair in parens) {
      pair.first.drop()
    }
    return totalCount != 0
  }

  override fun isWhitespaceOrComment(type: SyntaxElementType?): Boolean {
    return type != null && syntaxBuilder.isWhitespaceOrComment(type)
  }

  private fun CharSequence.crop(length: Int, addEllipses: Boolean): CharSequence {
    if (length >= this.length) return this
    return if (addEllipses) this.substring(0, length) + "..." else this.substring(0, length)
  }
}
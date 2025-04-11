// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.WhitespacesAndCommentsBinder
import org.jetbrains.annotations.Nls

internal class CompositeMarker(
  markerId: Int,
  builder: ParsingTreeBuilder,
) : ProductionMarker(markerId, builder), SyntaxTreeBuilder.Marker {

  lateinit var type: SyntaxElementType
  var endIndex: Int = -1

  override fun isErrorMarker(): Boolean = false

  val text: CharSequence?
    get() {
      if (!isDone) return null
      val originalText = builder.text
      val startOffset = getStartOffset() - builder.startOffset
      val endOffset = getEndOffset() - builder.startOffset
      val text = originalText.subSequence(startOffset, endOffset)
      check(text.length == endOffset - startOffset)
      return text
    }

  override fun dispose() {
    super.dispose()

    builder.myOptionalData.clean(markerId)

    endIndex = -1
  }

  override fun getEndOffset(): Int =
    builder.myLexStarts[endIndex] + builder.startOffset

  override fun getEndTokenIndex(): Int = endIndex

  override fun getErrorMessage(): String? =
    if (getNodeType() === SyntaxTokenTypes.ERROR_ELEMENT) builder.myOptionalData.getDoneError(markerId) else null

  override fun getNodeType(): SyntaxElementType =
    type

  override fun precede(): SyntaxTreeBuilder.Marker {
    @Suppress("UNCHECKED_CAST")
    return builder.precede(this)
  }

  override fun drop() {
    builder.myProduction.dropMarker(this)
  }

  override fun rollbackTo() {
    builder.rollbackTo(this)
  }

  override fun done(type: SyntaxElementType) {
    if (type == SyntaxTokenTypes.ERROR_ELEMENT) {
      builder.logger.warn("Error elements with empty message are discouraged. Please use builder.error() instead", RuntimeException())
    }
    this@CompositeMarker.type = type
    builder.processDone(this, null, null)
  }

  override fun collapse(type: SyntaxElementType) {
    done(type)
    builder.myOptionalData.markCollapsed(markerId)
  }

  override fun doneBefore(type: SyntaxElementType, before: SyntaxTreeBuilder.Marker) {
    if (type == SyntaxTokenTypes.ERROR_ELEMENT) {
      builder.logger.warn("Error elements with empty message are discouraged. Please use builder.errorBefore() instead", RuntimeException())
    }
    this@CompositeMarker.type = type
    builder.processDone(this, null, before as CompositeMarker)
  }

  override fun doneBefore(type: SyntaxElementType, before: SyntaxTreeBuilder.Marker, errorMessage: @Nls String) {
    val marker = before as CompositeMarker
    val errorNode = builder.pool.allocateErrorNode()
    errorNode.setErrorMessage(errorMessage)
    errorNode.startIndex = marker.getStartTokenIndex()
    builder.myProduction.addBefore(errorNode, marker)
    doneBefore(type, before)
  }

  override fun error(message: @Nls String) {
    type = SyntaxTokenTypes.ERROR_ELEMENT
    @Suppress("UNCHECKED_CAST")
    builder.processDone(this, message, null)
  }

  override fun errorBefore(message: @Nls String, before: SyntaxTreeBuilder.Marker) {
    type = SyntaxTokenTypes.ERROR_ELEMENT
    @Suppress("UNCHECKED_CAST")
    builder.processDone(this, message, before as CompositeMarker)
  }

  // TODO add this method to interface when it's required
  fun remapTokenType(newTokenType: SyntaxElementType) {
    type = newTokenType
  }

  override fun setCustomEdgeTokenBinders(left: WhitespacesAndCommentsBinder?, right: WhitespacesAndCommentsBinder?) {
    if (left != null) {
      @Suppress("UNCHECKED_CAST")
      builder.myOptionalData.assignBinder(markerId, left, false)
    }
    if (right != null) {
      @Suppress("UNCHECKED_CAST")
      builder.myOptionalData.assignBinder(markerId, right, true)
    }
  }

  val isDone: Boolean
    get() = endIndex != -1

  override fun toString(): String {
    if (getStartTokenIndex() < 0) return "<dropped>"
    val isDone = isDone
    val originalText = builder.text
    val startOffset = getStartOffset() - builder.startOffset
    val endOffset = if (isDone) getEndOffset() - builder.startOffset else builder.currentOffset
    val text = originalText.subSequence(startOffset, endOffset)
    return if (isDone) text.toString() else text.toString() + "\u2026"
  }

  override fun getLexemeIndex(done: Boolean): Int =
    if (done) endIndex else startIndex

  override fun setLexemeIndex(value: Int, done: Boolean) =
    if (done) endIndex = value else startIndex = value
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text

import com.intellij.ide.ui.text.paragraph.TextParagraph
import com.intellij.ide.ui.text.parts.TextPart
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.geom.Point2D
import javax.swing.JTextPane
import kotlin.math.roundToInt

/**
 * Implementation of [JTextPane] that support showing variously formatted and interactive text using [TextParagraph] and [TextPart] APIs.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
open class StyledTextPane : JTextPane(), Disposable {
  open var paragraphs: List<TextParagraph> = emptyList()
    set(value) {
      field = value
      redraw()
      repaint()
    }

  private val paragraphRanges: MutableList<IntRange> = mutableListOf()

  init {
    UIUtil.doNotScrollToCaret(this)
    isEditable = false

    val listener = object : MouseAdapter() {
      override fun mouseClicked(me: MouseEvent) {
        val offset = viewToModel2D(Point2D.Double(me.x.toDouble(), me.y.toDouble()))
        val (part, range) = findPartByOffset(offset) ?: return
        val onClickAction = part.onClickAction
        if (onClickAction != null) {
          val middle = (range.first + range.last) / 2
          val rectangle = modelToView2D(middle)
          onClickAction(this@StyledTextPane, Point(rectangle.x.roundToInt(), rectangle.y.roundToInt() + rectangle.height.roundToInt() / 2),
                        rectangle.height.roundToInt())
        }
      }

      override fun mouseMoved(me: MouseEvent) {
        val offset = viewToModel2D(Point2D.Double(me.x.toDouble(), me.y.toDouble()))
        val (part, _) = findPartByOffset(offset) ?: return
        cursor = if (part.onClickAction == null) {
          Cursor.getDefaultCursor()
        }
        else Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      }
    }
    addMouseListener(listener)
    addMouseMotionListener(listener)

    val connect = ApplicationManager.getApplication().messageBus.connect(this)
    connect.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
      override fun activeKeymapChanged(keymap: Keymap?) {
        redraw()
      }

      override fun shortcutsChanged(keymap: Keymap, actionIds: @NonNls MutableCollection<String>, fromSettings: Boolean) {
        redraw()
      }
    })
  }

  override fun addNotify() {
    super.addNotify()
    redraw()
  }

  override fun updateUI() {
    super.updateUI()
    invokeLater { redraw() }
  }

  open fun redraw() {
    clear()
    var curOffset = 0
    for ((ind, paragraph) in paragraphs.withIndex()) {
      val isLast = ind == paragraphs.lastIndex
      val start = curOffset
      curOffset = paragraph.insertToDocument(this, curOffset, isLast)
      paragraphRanges.add(start until curOffset)
    }
  }

  fun clear() {
    paragraphRanges.clear()
    text = ""
    highlighter.removeAllHighlights()
  }

  protected fun getParagraphRange(paragraph: TextParagraph): IntRange {
    val index = paragraphs.indexOf(paragraph)
    if (index != -1) {
      return paragraphRanges[index]
    }
    else throw IllegalArgumentException("Provided paragraph: $paragraph, do not contained inside this text pane")
  }

  protected fun findPartByOffset(offset: Int): Pair<TextPart, IntRange>? {
    val paragraphInd = paragraphRanges.indexOfFirst { offset in it }
    return if (paragraphInd != -1) {
      paragraphs[paragraphInd].findPartByOffset(offset)
    }
    else null
  }

  override fun getMaximumSize(): Dimension {
    return preferredSize
  }

  override fun dispose(): Unit = Unit

  final override fun addMouseListener(l: MouseListener?) {
    super.addMouseListener(l)
  }

  final override fun addMouseMotionListener(l: MouseMotionListener?) {
    super.addMouseMotionListener(l)
  }
}
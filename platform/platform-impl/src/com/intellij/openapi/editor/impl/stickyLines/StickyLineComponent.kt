// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.codeInsight.hint.EditorFragmentComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.ex.util.EditorUIUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.ui.MouseEventAdapter
import com.intellij.util.ui.MouseEventHandler
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.Graphics
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPopupMenu
import kotlin.math.max

/**
 * Represents one editor's line (gutter + line text)
 *
 * TODO: mouse events handling
 *  - the line should change background color on mouse hover
 *  - sticky gutter should forward clicks/hovers with adjusted coordinates to the actual gutter component
 */
internal class StickyLineComponent(private val editor: EditorEx) : JComponent() {
  var primaryVisualLine: Int = -1
  var scopeVisualLine: Int = -1
  private var offsetOnClick: Int = -1

  init {
    border = null
    addMouseListener(MyMouseEventHandler())
    addMouseListener(GutterMouseEventHandler())
  }

  fun setLine(primaryVisualLine: VisualPosition, scopeVisualLine: VisualPosition, offsetOnClick: Int) {
    this.primaryVisualLine = primaryVisualLine.line
    this.scopeVisualLine = scopeVisualLine.line
    this.offsetOnClick = offsetOnClick
  }

  fun resetLine() {
    this.primaryVisualLine = -1
    this.scopeVisualLine = -1
    this.offsetOnClick = -1
  }

  fun isEmpty(): Boolean {
    return primaryVisualLine == -1 || scopeVisualLine == -1 || offsetOnClick == -1
  }

  override fun paintComponent(g: Graphics) {
    assert(!isEmpty()) { "sticky panel should mark this line as not visible" }
    val stickyLine = primaryVisualLine
    val stickyLineY = editor.visualLineToY(stickyLine)
    val lineHeight = lineHeight()
    val lineWidth = lineWidth()

    // draw gutter
    val gutterComp = editor.gutterComponentEx
    val gutterWidth = max(1, gutterComp.width)
    val gutterImage: BufferedImage = UIUtil.createImage(editor.component, gutterWidth, lineHeight, BufferedImage.TYPE_INT_RGB)
    val gutterGraphics = gutterImage.graphics
    EditorUIUtil.setupAntialiasing(gutterGraphics)

    gutterGraphics.translate(0, -stickyLineY)
    gutterGraphics.setClip(0, stickyLineY, gutterComp.width, lineHeight)
    gutterGraphics.color = EditorFragmentComponent.getBackgroundColor(editor)
    gutterGraphics.fillRect(0, stickyLineY, gutterComp.width, lineHeight)
    gutterComp.paint(gutterGraphics) // TODO: hide icons (override, annotations, etc)

    // draw text
    val textWidth = lineWidth - gutterWidth
    val textImage: BufferedImage = UIUtil.createImage(editor.contentComponent, textWidth, lineHeight, BufferedImage.TYPE_INT_RGB)
    val textGraphics = textImage.graphics
    EditorUIUtil.setupAntialiasing(textGraphics)

    textGraphics.translate(0, -stickyLineY)
    textGraphics.setClip(0, stickyLineY, textWidth, lineHeight)
    val wasVisible = editor.setCaretVisible(false)
    try {
      editor.contentComponent.paint(textGraphics) // TODO: hide caret line background
    } finally {
      if (wasVisible) {
        editor.setCaretVisible(true)
      }
    }

    // apply images
    StartupUiUtil.drawImage(g, gutterImage, 0, 0, null)
    StartupUiUtil.drawImage(g, textImage, gutterComp.width, 0, null)
  }

  private fun lineWidth(): Int {
    return (editor as EditorImpl).stickyLinesPanelWidth
  }

  private fun lineHeight(): Int {
    return editor.lineHeight
  }

  inner class MyMouseEventHandler : MouseEventHandler() {
    private val popMenu: JPopupMenu
    private var isPopup = false

    init {
      val actionManager = ActionManager.getInstance()
      val actionGroup = actionManager.getAction("EditorStickyLinesSettings") as DefaultActionGroup
      popMenu = actionManager.createActionPopupMenu("StickyLine", actionGroup).component
    }

    override fun handle(event: MouseEvent) {
      if (event.isConsumed) return
      if (isGutterEvent(event)) {
        handleGutter(event)
      } else {
        handleLine(event)
      }
    }

    private fun handleLine(event: MouseEvent) {
      when (event.id) {
        MouseEvent.MOUSE_PRESSED -> {
          isPopup = event.isPopupTrigger
          if (isPopup) {
            popMenu.show(event.component, event.x, event.y)
          }
        }
        MouseEvent.MOUSE_CLICKED -> {
          if (!isPopup && !isEmpty()) {
            editor.caretModel.moveToOffset(offsetOnClick)
            editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
          }
        }
        MouseEvent.MOUSE_RELEASED,
        MouseEvent.MOUSE_ENTERED,
        MouseEvent.MOUSE_EXITED,
        MouseEvent.MOUSE_DRAGGED,
        MouseEvent.MOUSE_MOVED, -> {}
      }
      event.consume()
    }

    private fun handleGutter(event: MouseEvent) {
      // TODO: folding region collapsing/expanding
    }

    private fun isGutterEvent(event: MouseEvent): Boolean {
      return event.x <= editor.gutterComponentEx.width
    }
  }

  inner class GutterMouseEventHandler : MouseEventAdapter<EditorGutterComponentEx>(editor.gutterComponentEx) {
    override fun convert(event: MouseEvent): MouseEvent {
      return convert(event, editor.gutterComponentEx)
    }

    override fun getMouseListener(adapter: EditorGutterComponentEx): MouseListener? {
      if (adapter is MouseListener && adapter.isShowing) {
        return adapter
      }
      return null
    }
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.ex.util.EditorUIUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.util.ui.MouseEventAdapter
import com.intellij.util.ui.MouseEventHandler
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.Graphics
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPopupMenu
import javax.swing.JScrollPane

/**
 * Represents one editor's line (gutter + line text)
 */
internal class StickyLineComponent(private val editor: EditorEx) : JComponent() {
  var primaryVisualLine: Int = -1
  var scopeVisualLine: Int = -1
  private var offsetOnClick: Int = -1
  private var debugText: String? = null

  private var dumbTextImage: BufferedImage? = null
  private var isHovered: Boolean = false

  init {
    border = null
    val mouseEventHandler = MyMouseEventHandler()
    addMouseListener(mouseEventHandler)
    addMouseMotionListener(mouseEventHandler)
    addMouseWheelListener(MyMouseWheelListener())
    addMouseListener(GutterMouseEventHandler())
  }

  fun setLine(
    primaryVisualLine: VisualPosition,
    scopeVisualLine: VisualPosition,
    offsetOnClick: Int,
    debugText: String?,
  ) {
    setLine(primaryVisualLine.line, scopeVisualLine.line, offsetOnClick, debugText)
  }

  fun resetLine() {
    setLine(-1, -1, -1, null)
  }

  private fun setLine(
    primaryVisualLine: Int,
    scopeVisualLine: Int,
    offsetOnClick: Int,
    debugText: String?,
  ) {
    this.primaryVisualLine = primaryVisualLine
    this.scopeVisualLine = scopeVisualLine
    this.offsetOnClick = offsetOnClick
    this.debugText = debugText
  }

  fun isEmpty(): Boolean {
    return primaryVisualLine == -1 || scopeVisualLine == -1 || offsetOnClick == -1
  }

  fun startDumb() {
    // special mode when the line is rendered as an image to avoid flicking,
    // the mode ends when the corresponding editor's mode ends
    paintStickyLine(graphicsOrDumb = null)
  }

  override fun paintComponent(g: Graphics) {
    paintStickyLine(g)
  }

  private fun paintStickyLine(graphicsOrDumb: Graphics?) {
    assert(!isEmpty()) { "sticky panel should mark this line as not visible" }
    val stickyLineY = editor.visualLineToY(primaryVisualLine)
    val lineHeight = lineHeight()
    val gutterWidth = editor.gutterComponentEx.width
    val textWidth = lineWidth() - gutterWidth
    (editor as EditorImpl).isStickyLinePainting = true
    try {
      editor.isStickyLineHovered = isHovered
      if (graphicsOrDumb != null) {
        paintGutter(graphicsOrDumb, stickyLineY, lineHeight, gutterWidth)
        paintText(graphicsOrDumb, stickyLineY, lineHeight, gutterWidth, textWidth)
      } else {
        dumbTextImage = prepareDumbTextImage(stickyLineY, lineHeight, textWidth)
      }
    } finally {
      editor.isStickyLinePainting = false
    }
  }

  private fun paintGutter(g: Graphics, stickyLineY: Int, lineHeight: Int, gutterWidth: Int) {
    g.translate(0, -stickyLineY)
    g.setClip(0, stickyLineY, gutterWidth, lineHeight)
    editor.gutterComponentEx.paint(g)
  }

  private fun paintText(g: Graphics, stickyLineY: Int, lineHeight: Int, gutterWidth: Int, textWidth: Int) {
    g.translate(gutterWidth, 0)
    g.setClip(0, stickyLineY, textWidth, lineHeight)
    val textImage = dumbTextImage
    if (textImage != null && (editor as EditorImpl).isDumb) {
      StartupUiUtil.drawImage(g, textImage, 0, stickyLineY, null)
    } else {
      doPaintText(g)
      dumbTextImage = null
    }
  }

  private fun prepareDumbTextImage(stickyLineY: Int, lineHeight: Int, textWidth: Int): BufferedImage {
    val textImage = UIUtil.createImage(
      editor.contentComponent,
      textWidth,
      lineHeight,
      BufferedImage.TYPE_INT_RGB,
    )
    val textGraphics = textImage.graphics
    EditorUIUtil.setupAntialiasing(textGraphics)
    textGraphics.translate(0, -stickyLineY)
    textGraphics.setClip(0, stickyLineY, textWidth, lineHeight)
    doPaintText(textGraphics)
    textGraphics.dispose()
    return textImage
  }

  private fun doPaintText(g: Graphics) {
    editor.contentComponent.paint(g)
  }

  private fun lineWidth(): Int {
    return (editor as EditorImpl).stickyLinesPanelWidth
  }

  private fun lineHeight(): Int {
    return editor.lineHeight
  }

  override fun toString(): String {
    return "${debugText ?: ""}(primary=$primaryVisualLine, scope=$scopeVisualLine, onClick=$offsetOnClick)"
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
      when (event.id) {
        MouseEvent.MOUSE_ENTERED,
        MouseEvent.MOUSE_EXITED,
        MouseEvent.MOUSE_MOVED -> {
          handleHoverEvent(event)
        }
        else -> {
          if (isGutterEvent(event)) {
            handleGutter(event)
          } else {
            handleLine(event)
          }
        }
      }
    }

    private fun handleHoverEvent(event: MouseEvent) {
      when (event.id) {
        MouseEvent.MOUSE_ENTERED -> {
          isHovered = !isGutterEvent(event)
          if (isHovered) {
            repaint()
          }
        }
        MouseEvent.MOUSE_EXITED -> {
          isHovered = false
          repaint()
        }
        MouseEvent.MOUSE_MOVED -> {
          val isGutterEvent = isGutterEvent(event)
          if (isGutterEvent && isHovered || !isGutterEvent && !isHovered) {
            isHovered = !isHovered
            repaint()
          }
        }
        else -> throw IllegalArgumentException("unhandled event $event")
      }
      event.consume()
    }

    private fun handleLine(event: MouseEvent) {
      when (event.id) {
        MouseEvent.MOUSE_PRESSED -> {
          isPopup = event.isPopupTrigger
          if (isPopup) {
            popMenu.show(event.component, event.x, event.y)
          }
        }
        MouseEvent.MOUSE_RELEASED -> {
          // From review: on some platform RELEASED event can be a popup trigger
          if (!isPopup) {
            isPopup = event.isPopupTrigger
            if (isPopup) {
              popMenu.show(event.component, event.x, event.y)
            }
          }
        }
        MouseEvent.MOUSE_CLICKED -> {
          if (!isPopup && !isEmpty()) {
            // wrap into command to support "Back navigation" IJPL-591
            CommandProcessor.getInstance().executeCommand(
              editor.project,
              Runnable {
                editor.caretModel.moveToOffset(offsetOnClick)
                editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
                editor.selectionModel.removeSelection(/* allCarets = */ true)
                IdeDocumentHistory.getInstance(editor.project).includeCurrentCommandAsNavigation()
              },
              "",
              DocCommandGroupId.noneGroupId(editor.document),
              UndoConfirmationPolicy.DEFAULT,
              editor.document
            )
          }
        }
        MouseEvent.MOUSE_DRAGGED -> {}
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

  inner class MyMouseWheelListener : MouseEventAdapter<JScrollPane>(editor.scrollPane) {
    override fun convertWheel(event: MouseWheelEvent): MouseWheelEvent {
      return convert(event, editor.scrollPane) as MouseWheelEvent
    }

    override fun mouseWheelMoved(event: MouseWheelEvent?) {
      if (event == null || event.isConsumed) return
      editor.scrollPane.dispatchEvent(convertWheel(event))
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

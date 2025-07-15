// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.codeInsight.hints.presentation.InputHandler
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.LightweightHint
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.BitUtil
import com.intellij.util.IconUtil
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.Icon

internal class InlineBreakpointInlayRenderer(private val breakpoint: XLineBreakpointProxy?,
                                             private val variant: XLineBreakpointInlineVariantProxy?) : EditorCustomElementRenderer, InputHandler {
  // There could be three states:
  // * not-null breakpoint and not-null variant -- we have a breakpoint and a matching variant (normal case)
  // * null breakpoint and not-null variant -- we have a variant where breakpoint could be set (normal case)
  // * not-null breakpoint and null variant -- we have a breakpoint but no matching variant (outdated breakpoint?)
  init {
    require(breakpoint != null || variant != null)
  }

  // EditorCustomElementRenderer's methods have inlay as parameter,
  // but InputHandler's methods do not have it.
  lateinit var inlay: Inlay<InlineBreakpointInlayRenderer>

  var tooltipHint: LightweightHint? = null

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    val colorsScheme = inlay.editor.colorsScheme
    val font = UIUtil.getFontWithFallback(colorsScheme.getFont(EditorFontType.PLAIN))
    val context = FontInfo.getFontRenderContext(inlay.editor.contentComponent)
    val editorFontMetrics = FontInfo.getFontMetrics(font, context)

    val twoChars = "nn" // Use two average width characters (might be important for non-monospaced fonts).
    return editorFontMetrics.stringWidth(twoChars)
  }

  override fun paint(inlay: Inlay<*>,
                     g: Graphics,
                     targetRegion: Rectangle,
                     textAttributes: TextAttributes) {
    val component = inlay.editor.component

    val baseIcon: Icon
    val alpha: Float
    if (breakpoint != null) {
      baseIcon = breakpoint.getIcon()
      alpha = 1f
    }
    else {
      baseIcon = variant!!.icon
      // We use the same transparency as a breakpoint candidate in gutter.
      alpha = JBUI.getFloat("Breakpoint.iconHoverAlpha", 0.5f).coerceIn(0f, 1f)
    }

    // Icon might not fit into a two-character box if the font is really narrow,
    // however, it seems like a really rare case, so just ignore it.
    val editorScale = (inlay.editor as? EditorImpl)?.scale ?: 1f
    val iconScale = 0.75f * editorScale

    val scaledIcon = IconUtil.scale(baseIcon, component, iconScale)

    // Draw icon in the center of the region.
    val x = targetRegion.x + targetRegion.width / 2 - scaledIcon.iconWidth / 2
    val y = targetRegion.y + targetRegion.height / 2 - scaledIcon.iconHeight / 2
    GraphicsUtil.paintWithAlpha(g, alpha) { scaledIcon.paintIcon(component, g, x, y) }
  }

  override fun mousePressed(event: MouseEvent, translated: Point) =
    invokePopupIfNeeded(event)

  override fun mouseReleased(event: MouseEvent, translated: Point) =
    invokePopupIfNeeded(event)

  private fun invokePopupIfNeeded(event: MouseEvent) {
    if (event.isPopupTrigger) {
      if (breakpoint != null) {
        val center = centerPosition() ?: return
        val component = inlay.editor.contentComponent
        DebuggerUIUtil.showXBreakpointEditorBalloon(
          breakpoint.project,
          center.getPoint(component), component,
          false, breakpoint)
      }
      else {
        // FIXME[inline-bp]: show context like in gutter (XDebugger.Hover.Breakpoint.Context.Menu),
        //                   but actions should be adapted for inline breakpoints.
      }
      event.consume()
    }
  }

  private enum class ClickAction {
    SET, ENABLE_DISABLE, REMOVE
  }

  override fun mouseClicked(event: MouseEvent, translated: Point) {
    event.consume()

    val button = event.button
    var action: ClickAction? = null
    if (breakpoint != null) {
      // mimic gutter icon
      if (button == MouseEvent.BUTTON2 ||
          (button == MouseEvent.BUTTON1 && BitUtil.isSet(event.modifiersEx, InputEvent.ALT_DOWN_MASK))) {
        action =
          if (!Registry.`is`("debugger.click.disable.breakpoints")) ClickAction.ENABLE_DISABLE
          else ClickAction.REMOVE
      }
      else if (button == MouseEvent.BUTTON1) {
        action =
          if (Registry.`is`("debugger.click.disable.breakpoints")) ClickAction.ENABLE_DISABLE
          else ClickAction.REMOVE
      }
    }
    else {
      assert(variant != null)
      if (button == MouseEvent.BUTTON1) {
        action = ClickAction.SET
      }
    }

    if (action == null) return

    val editor = inlay.editor
    val project = editor.project ?: return
    val file = editor.virtualFile ?: return
    val offset = inlay.offset

    when (action) {
      ClickAction.SET -> {
        val line = editor.document.getLineNumber(offset)
        variant!!.createBreakpoint(project, file, line)
      }
      ClickAction.ENABLE_DISABLE -> {
        val proxy = breakpoint!!
        proxy.setEnabled(!proxy.isEnabled())
      }
      ClickAction.REMOVE -> {
        XDebuggerUtilImpl.removeBreakpointWithConfirmation(breakpoint)
      }
    }
  }

  override fun mouseMoved(event: MouseEvent, translated: Point) {
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
    showTooltip()
  }

  override fun mouseExited() {
    setCursor(null)
    hideTooltip()
  }

  private fun setCursor(cursor: Cursor?) =
    (inlay.editor as? EditorEx)?.setCustomCursor(InlineBreakpointInlayRenderer::class.java, cursor)

  private fun showTooltip() {
    if (tooltipHint?.isVisible == true) return
    if (!inlay.editor.contentComponent.isShowing) return

    val text = breakpoint?.getTooltipDescription() ?: variant!!.tooltipDescription
    val hint = LightweightHint(HintUtil.createInformationLabel(text))

    // Location policy: mimic gutter tooltip by pointing it to the center of an icon, but show it above the line.
    val constraint = HintManager.ABOVE
    val point = centerPosition() ?: return

    val hintPoint = HintManagerImpl.getHintPosition(hint, inlay.editor, point, constraint)

    val hintHint = HintManagerImpl.createHintHint(inlay.editor, hintPoint, hint, constraint)
      .setContentActive(false)
      .setPositionChangeShift(0, 0) // this tooltip points to the center of the icon, so no need to shift anything

    val flags = HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_TEXT_CHANGE or HintManager.HIDE_BY_SCROLLING
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, inlay.editor, hintPoint, flags, 0, false, hintHint)

    tooltipHint = hint
  }

  private fun hideTooltip() {
    tooltipHint?.hide()
    tooltipHint = null
  }

  private fun centerPosition(): RelativePoint? {
    val bounds = inlay.bounds ?: return null
    return RelativePoint(inlay.editor.contentComponent, Point(bounds.centerX.toInt(), bounds.centerY.toInt()))
  }

}

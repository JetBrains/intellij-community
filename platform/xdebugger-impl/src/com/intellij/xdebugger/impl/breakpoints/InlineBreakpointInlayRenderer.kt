// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.codeInsight.hints.presentation.InputHandler
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.BitUtil
import com.intellij.util.IconUtil
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.Icon
import kotlin.math.max
import kotlin.math.min

internal class InlineBreakpointInlayRenderer(private val breakpoint: XLineBreakpointImpl<*>?,
                                             private val variant: XLineBreakpointType<*>.XLineBreakpointVariant?) : EditorCustomElementRenderer, InputHandler {
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

  var hovered = false
    set(hovered) {
      val wasHovered = field
      field = hovered
      (inlay.editor as? EditorEx)?.setCustomCursor(InlineBreakpointInlayRenderer::class.java,
                                                   if (hovered) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else null)
      if (wasHovered != hovered) {
        inlay.repaint()
      }
    }

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
    var alpha: Float
    if (breakpoint != null) {
      baseIcon = breakpoint.icon
      alpha = 1f
    }
    else {
      baseIcon = variant!!.type.enabledIcon

      // FIXME[inline-bp]: do we need to rename the property?
      alpha = JBUI.getFloat("Breakpoint.iconHoverAlpha", 0.5f)
      alpha = max(0f, min(alpha, 1f))
      if (hovered) {
        // Slightly increase visibility (e.g. 0.5 -> 0.625).
        // FIXME[inline-bp]: ask Yulia Zozulya if we really need it?
        alpha = (3 * alpha + 1) / 4
      }
    }

    // FIXME[inline-bp]: introduce option to make inline icons slightly smaller than gutter ones
    // FIXME[inline-bp]: limit icon size to region size with some padding
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
        val bounds = inlay.bounds ?: return
        val center = Point(bounds.centerX.toInt(), bounds.centerY.toInt())
        DebuggerUIUtil.showXBreakpointEditorBalloon(
          breakpoint.project, center, inlay.editor.contentComponent, false, breakpoint)
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

    // FIXME[inline-bp]: what about removal by drag and drop?
    val editor = inlay.editor
    val project = editor.project ?: return
    val file = editor.virtualFile ?: return
    val offset = inlay.offset

    when (action) {
      ClickAction.SET -> {
        // FIXME[inline-bp]: is it ok to keep variant so long or should we obtain fresh variants and find similar one?
        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
        val line = editor.document.getLineNumber(offset)
        breakpointManager.addLineBreakpoint(variant!!.type as XLineBreakpointType<XBreakpointProperties<*>>,
                                            file.url, line,
                                            variant.createProperties(),
                                            false)
      }
      ClickAction.ENABLE_DISABLE -> {
        breakpoint!!.isEnabled = !breakpoint.isEnabled
      }
      ClickAction.REMOVE -> {
        if (XDebuggerUtilImpl.removeBreakpointWithConfirmation(breakpoint)) {
          // FIXME[inline-bp]: it's a dirty hack to render inlay as "hovered" just after we clicked on set breakpoint
          //       The problem is that after breakpoint removal we currently recreate all inlays and new ones would not be "hovered".
          //       So we manually propagate this property to future inlay at the same position.
          //       Otherwise there will be flickering:
          //       transparent -> (move mouse) -> hovered -> (click) -> set -> (click) -> transparent -> (move mouse 1px) -> hovered
          //                                                                              ^^^^^^^^^^^ this is bad
          //       One day we would keep old inlays and this hack would gone.
          for (newInlay in editor.inlayModel.getInlineElementsInRange(offset, offset, InlineBreakpointInlayRenderer::class.java)) {
            newInlay.renderer.hovered = true
          }
        }
      }
    }
  }

  override fun mouseMoved(event: MouseEvent, translated: Point) {
    hovered = true
  }

  override fun mouseExited() {
    hovered = false
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components

import com.intellij.util.ui.UIUtilities
import com.intellij.util.ui.JBInsets
import com.intellij.ide.ui.laf.darcula.DarculaLaf.isAltPressed
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.Shape
import java.beans.PropertyChangeEvent
import java.io.StringReader
import java.lang.Error
import java.net.URL
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.LookAndFeel.installProperty
import javax.swing.SwingConstants.LEADING
import javax.swing.SwingUtilities.layoutCompoundLabel
import javax.swing.UIManager
import javax.swing.event.ChangeEvent
import javax.swing.plaf.UIResource
import javax.swing.plaf.basic.BasicButtonListener
import javax.swing.plaf.basic.BasicButtonUI
import javax.swing.plaf.basic.BasicHTML
import javax.swing.text.BadLocationException
import javax.swing.text.Document
import javax.swing.text.Element
import javax.swing.text.View
import javax.swing.text.Position.Bias
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.ImageView
import javax.swing.text.html.StyleSheet

class DefaultLinkButtonUI : BasicButtonUI() {
  companion object {
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun createUI(c: JComponent?) = DefaultLinkButtonUI()
  }

  private var cached: View? = null

  override fun installDefaults(button: AbstractButton) {
    installProperty(button, "opaque", false)
    installProperty(button, "contentAreaFilled", false)
    installProperty(button, "borderPainted", false)
    installProperty(button, "rolloverEnabled", true)
    installProperty(button, "iconTextGap", 4)
    defaultTextShiftOffset = UIManager.getInt("Button.textShiftOffset")
    if (isUpdateable(button.font)) button.font = UIManager.getFont("Label.font")
    if (isUpdateable(button.background)) button.background = UIManager.getColor("Label.background")
    button.foreground = DynamicColor(button)
    button.horizontalAlignment = LEADING
  }

  override fun contains(c: JComponent?, x: Int, y: Int): Boolean {
    val button = c as? AbstractButton ?: return false
    val fm = button.getFontMetrics(button.font)
    val iconBounds = Rectangle()
    val textBounds = Rectangle()
    layoutText(button, fm, iconBounds, textBounds)
    return iconBounds.contains(x, y) || textBounds.contains(x, y) || iconBounds.union(textBounds).contains(x, y)
  }

  override fun paint(g: Graphics, c: JComponent?) {
    val button = c as? AbstractButton ?: return
    g.font = button.font
    val fm = UIUtilities.getFontMetrics(button, g)
    val iconBounds = Rectangle()
    val textBounds = Rectangle()
    val text = layoutText(button, fm, textBounds, iconBounds)

    if (isPressed(button)) setTextShiftOffset() else clearTextShiftOffset()

    paintIcon(g, button, iconBounds)

    if (text != null && text.isNotEmpty()) {
      val offset = textShiftOffset
      textBounds.x += offset
      textBounds.y += offset

      val underlined = isUnderlined(button)
      val view = button.getClientProperty(BasicHTML.propertyKey) as? View
      if (view == null) {
        g.color = button.foreground
        val index = if (isEnabled(button) && isAltPressed()) button.displayedMnemonicIndex else -1
        UIUtilities.drawStringUnderlineCharAt(button, g, text, index, textBounds.x, textBounds.y + fm.ascent)
        if (underlined) g.fillRect(textBounds.x, textBounds.y + fm.ascent + 1, textBounds.width, 1)
      }
      else if (underlined) {
        if (cached == null) {
          cached = createUnderlinedView(button, text)
        }
        cached!!.paint(g, textBounds)
      }
      else {
        view.paint(g, textBounds)
      }
    }
  }

  override fun createButtonListener(button: AbstractButton) = object : BasicButtonListener(button) {
    override fun propertyChange(event: PropertyChangeEvent) {
      cached = null // reinitialize underlined view on every property change
      super.propertyChange(event)
    }

    override fun stateChanged(event: ChangeEvent) {
      val source = event.source as? AbstractButton ?: return
      if (source.isRolloverEnabled) {
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        source.cursor = if (isHovered(source)) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else null
      }
      source.repaint()
    }
  }
}

private fun layoutText(button: AbstractButton, fm: FontMetrics, textBounds: Rectangle, iconBounds: Rectangle): String? {
  val viewBounds = Rectangle(button.width, button.height)
  JBInsets.removeFrom(viewBounds, button.insets)
  return layoutCompoundLabel(
    button, fm,
    button.text,
    button.icon,
    button.verticalAlignment,
    button.horizontalAlignment,
    button.verticalTextPosition,
    button.horizontalTextPosition,
    viewBounds, iconBounds, textBounds,
    if (button.text == null) 0 else button.iconTextGap)
}

private fun isUpdateable(property: Any?) = property == null || property is UIResource

private fun isEnabled(button: AbstractButton) = button.model?.isEnabled ?: false
private fun isHovered(button: AbstractButton) = button.model?.isRollover ?: false
private fun isPressed(button: AbstractButton) = button.model?.let { it.isArmed && it.isPressed } ?: false
private fun isVisited(button: AbstractButton) = (button as? ActionLink)?.visited ?: false
private fun isUnderlined(button: AbstractButton) = isHovered(button) || button.isFocusPainted && button.hasFocus()

// provide dynamic foreground color

private val defaultColor = JBUI.CurrentTheme.Link.linkColor()
private val hoveredColor = JBUI.CurrentTheme.Link.linkHoverColor()
private val pressedColor = JBUI.CurrentTheme.Link.linkPressedColor()
private val visitedColor = JBUI.CurrentTheme.Link.linkVisitedColor()
private val disabledColor = JBUI.CurrentTheme.Label.disabledForeground()
private fun getColor(button: AbstractButton) = when {
  !isEnabled(button) -> disabledColor
  isPressed(button) -> pressedColor
  isHovered(button) -> hoveredColor
  isVisited(button) -> visitedColor
  else -> defaultColor
}

private class DynamicColor(button: AbstractButton) : JBColor({ getColor(button) }), UIResource

// support underlined <html>

private fun createUnderlinedView(button: AbstractButton, text: String): View {
  val styles = StyleSheet()
  styles.addStyleSheet(sharedUnderlineStyles)
  styles.addStyleSheet(sharedEditorKit.styleSheet)
  styles.addRule(UIUtilities.displayPropertiesToCSS(button.font, button.foreground))

  val document = HTMLDocument(styles)
  document.asynchronousLoadPriority = Int.MAX_VALUE // load everything in one chunk
  document.preservesUnknownTags = false // do not display unknown tags

  val base = button.getClientProperty(BasicHTML.documentBaseKey)
  if (base is URL) document.base = base

  readSafely(text) { sharedEditorKit.read(it, document, 0) }
  return UnderlinedView(button, sharedEditorKit.viewFactory.create(document.defaultRootElement))
}

private fun readSafely(text: String, read: (StringReader) -> Unit) {
  val reader = StringReader(text)
  try {
    read(reader)
  }
  catch (error: Throwable) {
  }
  finally {
    reader.close()
  }
}

private const val underlineStyles = """
p { margin-top: 0; margin-bottom: 0; margin-left: 0; margin-right: 0; text-decoration: underline }
body { margin-top: 0; margin-bottom: 0; margin-left: 0; margin-right: 0; text-decoration: underline }
font {text-decoration: underline}
"""

private val sharedUnderlineStyles by lazy {
  val styles = StyleSheet()
  readSafely(underlineStyles) { styles.loadRules(it, null) }
  styles
}

private val sharedEditorKit by lazy {
  object : HTMLEditorKit() {
    override fun getViewFactory() = lazyViewFactory

    private val lazyViewFactory by lazy {
      object : HTMLEditorKit.HTMLFactory() {
        override fun create(elem: Element): View {
          val view = super.create(elem)
          if (view is ImageView) {
            // force images to be loaded synchronously
            view.loadsSynchronously = true
          }
          return view
        }
      }
    }
  }
}

private class UnderlinedView(private val button: AbstractButton, private val view: View) : View(null) {
  private var width = 0f

  init {
    view.parent = this
    // initially layout to the preferred size
    setSize(view.getPreferredSpan(X_AXIS), view.getPreferredSpan(Y_AXIS))
  }

  override fun paint(g: Graphics, shape: Shape) {
    val bounds = shape.bounds
    view.setSize(bounds.width.toFloat(), bounds.height.toFloat())
    view.paint(g, shape)
  }

  override fun getViewFactory() = sharedEditorKit.viewFactory
  override fun setParent(parent: View) = throw Error("Can't set parent on root view")

  override fun getContainer() = button
  override fun preferenceChanged(child: View, width: Boolean, height: Boolean) {
    button.revalidate()
    button.repaint()
  }

  override fun getViewCount() = 1
  override fun getView(n: Int) = view

  override fun getElement(): Element = view.element
  override fun getAttributes() = null

  override fun getDocument(): Document = view.document
  override fun getStartOffset() = view.startOffset
  override fun getEndOffset() = view.endOffset
  override fun getAlignment(axis: Int) = view.getAlignment(axis)
  override fun getMaximumSpan(axis: Int) = Int.MAX_VALUE.toFloat()
  override fun getMinimumSpan(axis: Int) = view.getMinimumSpan(axis)
  override fun getPreferredSpan(axis: Int) = if (axis == X_AXIS) width else view.getPreferredSpan(axis)
  override fun setSize(width: Float, height: Float) {
    this.width = width
    view.setSize(width, height)
  }

  override fun viewToModel(x: Float, y: Float, shape: Shape, bias: Array<Bias>) = view.viewToModel(x, y, shape, bias)

  @Throws(BadLocationException::class)
  override fun modelToView(pos: Int, shape: Shape, bias: Bias): Shape = view.modelToView(pos, shape, bias)

  @Throws(BadLocationException::class)
  override fun modelToView(pos0: Int, bias0: Bias, pos1: Int, bias1: Bias, shape: Shape): Shape =
    view.modelToView(pos0, bias0, pos1, bias1, shape)
}

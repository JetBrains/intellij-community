// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ide.ui.laf.darcula.DarculaLaf.isAltPressed
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.JBColor
import com.intellij.ui.paint.RectanglePainter
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI.CurrentTheme.Link
import com.intellij.util.ui.UIUtilities
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.beans.PropertyChangeEvent
import java.io.StringReader
import java.net.URL
import java.util.function.Supplier
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
import javax.swing.text.Position.Bias
import javax.swing.text.View
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.ImageView
import javax.swing.text.html.StyleSheet

internal class DefaultLinkButtonUI : BasicButtonUI() {
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
    if (!button.isFontSet || button.font is UIResource) button.font = UIManager.getFont("Label.font")!!
    if (!button.isBackgroundSet || button.background is UIResource) button.background = UIManager.getColor("Label.background")
    if (!button.isForegroundSet || button.foreground is UIResource) button.foreground = DynamicColor(button)
    button.horizontalAlignment = LEADING
    button.isRequestFocusEnabled = false
  }

  override fun getBaseline(c: JComponent?, width: Int, height: Int): Int {
    if (width < 0 || height < 0) return -1
    val button = c as? AbstractButton ?: return -1
    val text = button.text ?: return -1
    if (text.isEmpty()) return -1
    val layout = Layout(button, Rectangle(width, height))
    val view = htmlView(button) ?: return layout.baseline
    val baseline = BasicHTML.getHTMLBaseline(view, layout.textBounds.width, layout.textBounds.height)
    return if (baseline < 0) baseline else baseline + layout.textBounds.y
  }

  override fun getPreferredSize(c: JComponent?): Dimension {
    val button = c as? AbstractButton ?: return Dimension()
    val max = Short.MAX_VALUE.toInt()
    val layout = Layout(button, Rectangle(max, max))
    return layout.bounds.size.also {
      JBInsets.addTo(it, button.insets)
      JBInsets.addTo(it, button.focusInsets())
    }
  }

  override fun contains(c: JComponent?, x: Int, y: Int): Boolean {
    val button = c as? AbstractButton ?: return false
    val layout = Layout(button, button.viewBounds())
    return layout.iconBounds.contains(x, y) || layout.textBounds.contains(x, y) || layout.bounds.contains(x, y)
  }

  override fun paint(g: Graphics, c: JComponent?) {
    val button = c as? AbstractButton ?: return
    g.font = button.font
    val layout = Layout(button, button.viewBounds(), UIUtilities.getFontMetrics(button, g))

    if (isPressed(button)) setTextShiftOffset() else clearTextShiftOffset()

    paintIcon(g, button, layout.iconBounds)

    if (layout.text.isNotEmpty()) {
      val offset = textShiftOffset
      layout.textBounds.x += offset
      layout.textBounds.y += offset

      val hovered = isHovered(button)
      val view = htmlView(button)
      if (view == null) {
        g.color = getTextColor(button)
        val index = if (isEnabled(button) && isAltPressed()) button.displayedMnemonicIndex else -1
        UIUtilities.drawStringUnderlineCharAt(button, g, layout.text, index, layout.textBounds.x, layout.baseline)
        if (hovered) g.fillRect(layout.textBounds.x, layout.baseline + 1, layout.textBounds.width, 1)
      }
      else if (hovered) {
        if (cached == null) {
          cached = createUnderlinedView(button, layout.text)
        }
        cached!!.paint(g, layout.textBounds)
      }
      else {
        view.paint(g, layout.textBounds)
      }
    }
    if (g is Graphics2D && isFocused(button)) {
      g.color = Link.FOCUSED_BORDER_COLOR
      val bounds = layout.bounds.also { JBInsets.addTo(it, button.focusInsets()) }
      val round = Registry.intValue("ide.link.button.focus.round.arc", 4)
      RectanglePainter.DRAW.paint(g, bounds.x, bounds.y, bounds.width, bounds.height, scale(round))
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

private class Layout(button: AbstractButton, viewBounds: Rectangle, val fm: FontMetrics = button.getFontMetrics(button.font)) {
  val iconBounds = Rectangle()
  val textBounds = Rectangle()
  val text = layoutCompoundLabel(
    button, fm,
    button.text,
    button.icon,
    button.verticalAlignment,
    button.horizontalAlignment,
    button.verticalTextPosition,
    button.horizontalTextPosition,
    viewBounds, iconBounds, textBounds,
    button.iconTextGap) ?: ""

  val baseline: Int
    get() = textBounds.y + fm.ascent

  val bounds: Rectangle
    get() = iconBounds.union(textBounds)
}

private fun AbstractButton.viewBounds() = Rectangle(width, height).also {
  JBInsets.removeFrom(it, insets)
  JBInsets.removeFrom(it, focusInsets())
}

private fun AbstractButton.focusInsets(): Insets? {
  if (!isFocusPainted) return null
  val margin = scale(1)
  return Insets(0, margin, 0, margin)
}

private fun isEnabled(button: AbstractButton) = button.model?.isEnabled ?: false
private fun isHovered(button: AbstractButton) = button.model?.isRollover ?: false
private fun isPressed(button: AbstractButton) = button.model?.let { it.isArmed && it.isPressed } ?: false
private fun isVisited(button: AbstractButton) = (button as? ActionLink)?.visited ?: false
private fun isFocused(button: AbstractButton) = button.isFocusPainted && button.hasFocus() && !isPressed(button)

// provide dynamic foreground color

private fun getTextColor(button: AbstractButton) = when {
  !isEnabled(button) -> Link.Foreground.DISABLED
  else -> button.foreground ?: getLinkColor(button)
}

private fun getLinkColor(button: AbstractButton) = when {
  isPressed(button) -> Link.Foreground.PRESSED
  isHovered(button) -> Link.Foreground.HOVERED
  isVisited(button) -> Link.Foreground.VISITED
  else -> Link.Foreground.ENABLED
}

private class DynamicColor(button: AbstractButton) : UIResource, JBColor(Supplier { getLinkColor(button) })

// support underlined <html>

private fun htmlView(button: AbstractButton) = button.getClientProperty(BasicHTML.propertyKey) as? View

private fun createUnderlinedView(button: AbstractButton, text: String): View {
  val styles = StyleSheet()
  styles.addStyleSheet(sharedUnderlineStyles)
  styles.addStyleSheet(sharedEditorKit.styleSheet)
  styles.addRule(UIUtilities.displayPropertiesToCSS(button.font, getTextColor(button)))

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
  catch (_: Throwable) {
  }
  finally {
    reader.close()
  }
}

@NonNls
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
  HTMLEditorKitBuilder().withViewFactoryExtensions(
    { _, view ->
      if (view is ImageView) {
        // force images to be loaded synchronously
        view.loadsSynchronously = true
      }
      view
    }).build()
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

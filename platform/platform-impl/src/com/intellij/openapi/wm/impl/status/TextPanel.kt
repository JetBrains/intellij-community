// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.wm.impl.status

import com.intellij.ide.ui.AntialiasingType
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl.WidgetEffect
import com.intellij.ui.ClientProperty
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.*
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.awt.*
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.*

open class TextPanel @JvmOverloads constructor(private val toolTipTextSupplier: (() -> String?)? = null) : JPanel(), Accessible {
  /**
   * @return the text that is used to calculate the preferred size
   */
  protected open val textForPreferredSize: @Nls String?
    get() = text

  private var preferredHeight: Int? = null
  private var explicitSize: Dimension? = null
  protected var alignment: Float = 0f
    private set

  init {
    isOpaque = false
    @Suppress("LeakingThis")
    updateUI()
  }

  companion object {
    const val PROPERTY_TEXT: String = "TextPanel.text"
    const val PROPERTY_ICON: String = "TextPanel.icon"

    fun getFont(): Font = if (SystemInfoRt.isMac && !ExperimentalUI.isNewUI()) JBFont.small() else JBUI.CurrentTheme.StatusBar.font()

    fun computeTextHeight(): Int {
      val label = JLabel("XXX") //NON-NLS
      label.font = getFont()
      return label.preferredSize.height
    }
  }

  override fun getToolTipText(): String? = toolTipTextSupplier?.invoke() ?: super.getToolTipText()

  override fun updateUI() {
    GraphicsUtil.setAntialiasingType(this, AntialiasingType.getAATextInfoForSwingComponent())
    UISettings.setupFractionalMetrics(this)
    recomputeSize()
  }

  override fun getFont(): Font = Companion.getFont()

  fun recomputeSize() {
    preferredHeight = computeTextHeight()
  }

  override fun paintComponent(g: Graphics) {
    var s: @Nls String = text ?: return

    val panelWidth = width
    val panelHeight = height

    g as Graphics2D
    g.font = font
    setupAntialiasing(g)
    val bounds = Rectangle(panelWidth, panelHeight)
    val fontMetrics = g.getFontMetrics()
    val textWidth = fontMetrics.stringWidth(s)
    val insets = insets
    val x = if (textWidth > panelWidth) insets.left else getTextX(g)
    val maxWidth = panelWidth - x - insets.right
    if (textWidth > maxWidth) {
      s = truncateText(text = s, bounds = bounds, fm = fontMetrics, textR = Rectangle(), iconR = Rectangle(), maxWidth = maxWidth)
    }

    var y = UIUtil.getStringY(s, bounds, g)
    if (SystemInfo.isJetBrainsJvm && ExperimentalUI.isNewUI()) {
      // see SimpleColoredComponent.getTextBaseline
      y += fontMetrics.leading
    }

    val effect = getWidgetEffect()
    val foreground: Color = if (isEnabled) {
      when (effect) {
        WidgetEffect.PRESSED -> JBUI.CurrentTheme.StatusBar.Widget.PRESSED_FOREGROUND
        WidgetEffect.HOVER -> JBUI.CurrentTheme.StatusBar.Widget.HOVER_FOREGROUND
        else -> JBUI.CurrentTheme.StatusBar.Widget.FOREGROUND
      }
    }
    else {
      NamedColorUtil.getInactiveTextColor()
    }
    g.color = foreground
    g.drawString(s, x, y)
  }

  private fun getWidgetEffect(): WidgetEffect? {
    return ClientProperty.get(this, IdeStatusBarImpl.WIDGET_EFFECT_KEY)
  }

  @TestOnly
  fun isHoverEffect(): Boolean {
    return isEnabled && getWidgetEffect() == WidgetEffect.HOVER
  }

  protected open fun getTextX(g: Graphics): Int {
    val text = text
    val insets = insets
    return when {
      text == null || alignment == LEFT_ALIGNMENT -> insets.left
      alignment == RIGHT_ALIGNMENT -> width - insets.right - g.fontMetrics.stringWidth(text)
      alignment == CENTER_ALIGNMENT -> (width - insets.left - insets.right - g.fontMetrics.stringWidth(text)) / 2 + insets.left
      else -> insets.left
    }
  }

  protected open fun truncateText(text: @Nls String,
                                  bounds: Rectangle?,
                                  fm: FontMetrics?,
                                  textR: Rectangle?,
                                  iconR: Rectangle?,
                                  maxWidth: Int): @Nls String {
    return SwingUtilities.layoutCompoundLabel(this, fm, text, null, SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER,
                                              SwingConstants.TRAILING,
                                              bounds, iconR, textR, 0)
  }

  fun setTextAlignment(alignment: Float) {
    this.alignment = alignment
  }

  var text: @Nls String? = null
    set(value) {
      val text = value?.takeIf(String::isNotEmpty)
      if (text == field) {
        return
      }

      val accessibleContext = accessibleContext
      val oldAccessibleName = accessibleContext?.accessibleName
      val oldText = field
      field = text
      firePropertyChange(PROPERTY_TEXT, oldText, text)
      if (accessibleContext != null && accessibleContext.accessibleName != oldAccessibleName) {
        accessibleContext.firePropertyChange(
          AccessibleContext.ACCESSIBLE_VISIBLE_DATA_PROPERTY,
          oldAccessibleName,
          accessibleContext.accessibleName
        )
      }
      preferredSize = getPanelDimensionFromFontMetrics(text)
      revalidate()
      repaint()
    }

  override fun getPreferredSize(): Dimension = explicitSize ?: getPanelDimensionFromFontMetrics(textForPreferredSize)

  private fun getPanelDimensionFromFontMetrics(text: String?): Dimension {
    val insets = insets
    val width = insets.left + insets.right + (if (text == null) 0 else getFontMetrics(font).stringWidth(text))
    return Dimension(width, preferredHeight ?: minimumSize.height)
  }

  fun setExplicitSize(explicitSize: Dimension?) {
    this.explicitSize = explicitSize
  }

  open class WithIconAndArrows : TextPanel {
    companion object {
      private val GAP = JBUIScale.scale(2)
    }

    open var icon: Icon? = null
      set(value) {
        if (value == field) {
          return
        }

        val oldValue = field
        field = value
        firePropertyChange(PROPERTY_ICON, oldValue, value)

        revalidate()
        repaint()
      }

    constructor() : super(null)
    constructor(toolTipTextSupplier: (() -> String?)?) : super(toolTipTextSupplier)

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      val icon = if (icon == null || isEnabled) icon else IconLoader.getDisabledIcon(icon!!)
      icon?.paintIcon(this, g, getIconX(g), height / 2 - icon.iconHeight / 2)
    }

    override fun getPreferredSize(): Dimension {
      val preferredSize = super.getPreferredSize()
      if (icon == null) {
        return preferredSize
      }
      else {
        return Dimension((preferredSize.width + icon!!.iconWidth).coerceAtLeast(height), preferredSize.height)
      }
    }

    override fun getTextX(g: Graphics): Int {
      val x = super.getTextX(g)
      return when {
        icon == null || alignment == RIGHT_ALIGNMENT -> x
        alignment == CENTER_ALIGNMENT -> x + (icon!!.iconWidth + GAP) / 2
        alignment == LEFT_ALIGNMENT -> x + icon!!.iconWidth + GAP
        else -> x
      }
    }

    private fun getIconX(g: Graphics): Int {
      val x = super.getTextX(g)
      return when {
        icon == null || text == null || alignment == LEFT_ALIGNMENT -> x
        alignment == CENTER_ALIGNMENT -> x - (icon!!.iconWidth + GAP) / 2
        alignment == RIGHT_ALIGNMENT -> x - icon!!.iconWidth - GAP
        else -> x
      }
    }

    fun hasIcon(): Boolean = icon != null
  }

  // used externally
  @Suppress("unused")
  open class ExtraSize : TextPanel(null) {
    override fun getPreferredSize(): Dimension {
      val size = super.getPreferredSize()
      return Dimension(size.width + 3, size.height)
    }
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessibleTextPanel()
    }
    return accessibleContext
  }

  private inner class AccessibleTextPanel : AccessibleJComponent() {
    override fun getAccessibleRole(): AccessibleRole = AccessibleRole.LABEL

    override fun getAccessibleName(): String? = text
  }
}
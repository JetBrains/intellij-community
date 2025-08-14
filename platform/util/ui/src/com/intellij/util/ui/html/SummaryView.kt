// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.ScalableIcon
import com.intellij.util.asSafely
import com.intellij.util.ui.ExtendableHTMLViewFactory
import org.jetbrains.annotations.ApiStatus
import java.awt.AlphaComposite
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Shape
import javax.swing.Icon
import javax.swing.text.AttributeSet
import javax.swing.text.Element

internal class SummaryView(elem: Element, axis: Int) : BlockViewEx(elem, axis) {

  companion object {
    @JvmField
    val EXPANDED: Any = "expanded"
  }

  var expanded: Boolean = false
    private set

  private val isDetailsSummaryView
    get() =
      parent.element.name == "details" && parent.getView(0) === this

  private val chevronIcon: Icon
    get() {
      val icon = if (expanded)
        AllIcons.General.ChevronUp
      else
        AllIcons.General.ChevronDown
      val scaleFactor = container.asSafely<ExtendableHTMLViewFactory.ScaledHtmlJEditorPane>()?.contentsScaleFactor ?: 1f
      return if (icon is ScalableIcon && scaleFactor != 1f)
        icon.scale(scaleFactor)
      else
        icon
    }

  @ApiStatus.Internal
  override fun setPropertiesFromAttributes() {
    super.setPropertiesFromAttributes()
    if (isDetailsSummaryView) {
      setInsets(topInset, leftInset, bottomInset, (rightInset + chevronIcon.iconWidth).toShort())
    }
    expanded = element.attributes.getAttribute(HTML_Tag_DETAILS)
      ?.asSafely<AttributeSet>()?.getAttribute(EXPANDED) == true
  }

  override fun paint(g: Graphics, a: Shape) {
    super.paint(g, a)
    if (isDetailsSummaryView) {
      val g2d = g as Graphics2D
      val savedComposite = g2d.composite
      g2d.composite = AlphaComposite.SrcOver // support transparency
      val icon = chevronIcon
      icon.paintIcon(null, g, a.bounds.x + a.bounds.width - rightInset, a.bounds.y + topInset)
      g2d.composite = savedComposite
    }
  }

}
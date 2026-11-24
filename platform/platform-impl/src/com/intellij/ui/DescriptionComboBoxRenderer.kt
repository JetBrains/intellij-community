// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.setToolTipText
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JList

/**
 * A [GroupedComboBoxRenderer] that shows a gray description under the text of an item of the popup.
 * The description starts under the text, after the icon.
 * When [getIcon] gives an icon for an item of the popup, an item without an icon gets an empty icon, so the texts stay aligned.
 * The collapsed combo box shows only the icon and the text of the selected item.
 *
 * @param descriptionWidth the maximum width of the description in unscaled pixels. A longer description wraps.
 */
@ApiStatus.Internal
abstract class DescriptionComboBoxRenderer<T>(
  component: JComponent? = null,
  private val descriptionWidth: Int = 340,
) : GroupedComboBoxRenderer<T>(component) {
  private lateinit var texts: NonOpaquePanel
  private lateinit var description: JBLabel
  private var descriptionText: @Nls String? = null

  /** `true` when [getIcon] gives an icon for an item of the rendered list. */
  private var listHasIcons = false

  abstract fun getDescription(item: T): @Nls String?

  open fun getToolTipText(item: T): @NlsContexts.Tooltip String? = null

  override fun layout() {
    // no fixed row height, because an item with a description takes two rows
    myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH)
    myRendererComponent.add(NonOpaquePanel(itemComponent), BorderLayout.CENTER)
  }

  override fun layoutComponent(component: JComponent): JComponent {
    description = JBLabel()
    texts = NonOpaquePanel(BorderLayout())
    texts.add(component, BorderLayout.CENTER)
    texts.add(description, BorderLayout.SOUTH)
    return super.layoutComponent(texts)
  }

  override fun getListCellRendererComponent(
    list: JList<out T>?,
    value: T,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    val model = list?.model
    listHasIcons = model != null && (0 until model.size).any { getIcon(model.getElementAt(it)) != null }
    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    myRendererComponent.setToolTipText(if (index >= 0) getToolTipText(value)?.let(HtmlChunk::text) else null)
    return component
  }

  override fun customize(
    item: SimpleColoredComponent,
    value: T,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ) {
    super.customize(item, value, index, isSelected, cellHasFocus)
    if (index >= 0 && listHasIcons && item.icon == null) {
      item.icon = EmptyIcon.ICON_16
    }
    val text = getDescription(value)
    descriptionText = text
    description.isVisible = index >= 0 && text != null
    if (index < 0 || text == null) {
      texts.border = JBUI.Borders.empty()
      return
    }

    texts.border = JBUI.Borders.empty(4, 0)
    val iconWidth = item.icon?.let { it.iconWidth + item.iconTextGap } ?: 0
    description.border = BorderFactory.createEmptyBorder(
      JBUI.scale(2),
      item.insets.left + item.ipad.left + iconWidth,
      0,
      0,
    )
    description.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    val maxWidth = JBUI.scale(descriptionWidth)
    val body = if (description.getFontMetrics(description.font).stringWidth(text) <= maxWidth) {
      HtmlChunk.text(text)
    }
    else {
      HtmlChunk.text(text).wrapWith(HtmlChunk.body().style("width: ${maxWidth}px"))
    }
    description.text = body.wrapWith(HtmlChunk.html()).toString()
  }

  override fun getDelegateAccessibleDescription(): String? = descriptionText
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.components.htmlComponent
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.text.JTextComponent

internal const val DSL_LABEL_NO_BOTTOM_GAP_PROPERTY = "dsl.label.no.bottom.gap"

/**
 * Components that can have assigned labels
 */
private val ALLOWED_LABEL_COMPONENTS = listOf(
  JComboBox::class,
  JSlider::class,
  JSpinner::class,
  JTextComponent::class
)

internal val JComponent.origin: JComponent
  get() {
    return when (this) {
      is TextFieldWithBrowseButton -> textField
      else -> this
    }
  }

internal fun createHtmlComment(text: String, action: (HyperlinkEvent) -> Unit): JEditorPane {
  val hyperlinkAdapter = object : HyperlinkAdapter() {
    override fun hyperlinkActivated(e: HyperlinkEvent) {
      action.invoke(e)
    }
  }
  @Suppress("HardCodedStringLiteral")
  val processedText = text.replace("<a>", "<a href=''>", ignoreCase = true)
  val font = ComponentPanelBuilder.getCommentFont(UIUtil.getLabelFont())
  return htmlComponent(processedText, font = font, foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND,
    hyperlinkListener = hyperlinkAdapter)
}

internal fun isAllowedLabel(cell: CellBaseImpl<*>?): Boolean {
  return cell is CellImpl<*> && ALLOWED_LABEL_COMPONENTS.any { clazz -> clazz.isInstance(cell.component.origin) }
}

internal fun labelCell(label: JLabel, cell: CellBaseImpl<*>?) {
  if (isAllowedLabel(cell)) {
    label.labelFor = (cell as CellImpl<*>).component.origin
  }
}

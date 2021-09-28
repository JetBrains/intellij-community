// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.components.htmlComponent
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
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

internal fun createHtmlComment(text: String, action: HyperlinkEventAction): JEditorPane {
  return createHtmlPane(text, action, JBUI.CurrentTheme.ContextHelp.FOREGROUND)
}

internal fun createHtml(text: String, action: HyperlinkEventAction): JEditorPane {
  return createHtmlPane(text, action, JBUI.CurrentTheme.Label.foreground())
}

private fun createHtmlPane(text: String, action: HyperlinkEventAction, foreground: Color? = null): JEditorPane {
  val hyperlinkAdapter = HyperlinkListener { e ->
    when (e?.eventType) {
      HyperlinkEvent.EventType.ACTIVATED -> action.hyperlinkActivated(e)
      HyperlinkEvent.EventType.ENTERED -> action.hyperlinkEntered(e)
      HyperlinkEvent.EventType.EXITED -> action.hyperlinkExited(e)
    }
  }

  @Suppress("HardCodedStringLiteral")
  val processedText = text.replace("<a>", "<a href=''>", ignoreCase = true)
  val font = ComponentPanelBuilder.getCommentFont(UIUtil.getLabelFont())
  return htmlComponent(processedText, font = font, foreground = foreground, hyperlinkListener = hyperlinkAdapter)
}

internal fun isAllowedLabel(cell: CellBaseImpl<*>?): Boolean {
  return cell is CellImpl<*> && ALLOWED_LABEL_COMPONENTS.any { clazz -> clazz.isInstance(cell.component.origin) }
}

internal fun labelCell(label: JLabel, cell: CellBaseImpl<*>?) {
  if (isAllowedLabel(cell)) {
    label.labelFor = (cell as CellImpl<*>).component.origin
  }
}

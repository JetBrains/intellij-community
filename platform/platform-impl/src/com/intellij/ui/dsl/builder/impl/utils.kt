// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.htmlComponent
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import javax.swing.text.JTextComponent

/**
 * Internal component properties for UI DSL
 */
@ApiStatus.Internal
internal enum class DslComponentPropertyInternal {
  /**
   * Removes standard bottom gap from label
   */
  LABEL_NO_BOTTOM_GAP,

  /**
   * Baseline for component should be obtained from [JComponent.font] property
   */
  BASELINE_FROM_FONT,
}

/**
 * Throws exception instead of logging warning. Useful while forms building to avoid layout mistakes
 */
private const val FAIL_ON_WARN = false

private val LOG = Logger.getInstance("Jetbrains UI DSL")

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

internal fun createComment(@NlsContexts.Label text: String, maxLineLength: Int, action: HyperlinkEventAction): DslLabel {
  val result = DslLabel("")
  result.action = action
  result.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
  result.font = ComponentPanelBuilder.getCommentFont(UIUtil.getLabelFont())
  result.setHtmlText(text, maxLineLength)
  return result
}

internal fun createCommentNoWrap(@NlsContexts.Label text: String): DslLabel {
  val result = DslLabel(text)
  result.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
  result.font = ComponentPanelBuilder.getCommentFont(UIUtil.getLabelFont())
  return result
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
  val result = htmlComponent(processedText, font = font, foreground = foreground, hyperlinkListener = hyperlinkAdapter)
  // JEditorPane doesn't support baseline, calculate it manually from font
  result.putClientProperty(DslComponentPropertyInternal.BASELINE_FROM_FONT, true)
  return result
}

internal fun isAllowedLabel(cell: CellBaseImpl<*>?): Boolean {
  return cell is CellImpl<*> && ALLOWED_LABEL_COMPONENTS.any { clazz -> clazz.isInstance(cell.component.origin) }
}

internal fun labelCell(label: JLabel, cell: CellBaseImpl<*>?) {
  if (isAllowedLabel(cell)) {
    label.labelFor = (cell as CellImpl<*>).component.origin
  }
}

internal fun warn(message: String) {
  if (FAIL_ON_WARN) {
    throw UiDslException(message)
  }
  else {
    LOG.warn(message)
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import java.awt.Cursor
import javax.swing.event.HyperlinkEvent

class HtmlInfoPanel : Wrapper() {
  private var currentSeverity: Severity? = null
  private var currentLinkActivationListener: ((HyperlinkEvent) -> Unit)? = null

  private val errorPane = HtmlEditorPane().apply {
    removeHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
    addHyperlinkListener { e ->
      if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        currentLinkActivationListener?.invoke(e)
      }
      else {
        cursor = if (e.eventType == HyperlinkEvent.EventType.ENTERED) Cursor(Cursor.HAND_CURSOR)
        else Cursor(Cursor.DEFAULT_CURSOR)
      }
    }
    isFocusable = false
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP)
  }

  init {
    setContent(errorPane)
    isOpaque = true
    isVisible = false
  }

  fun setInfo(text: String?, severity: Severity = Severity.INFO, linkActivationListener: ((HyperlinkEvent) -> Unit)? = null) {
    if (text == null) {
      errorPane.text = ""
      currentSeverity = null
      currentLinkActivationListener = null
      isVisible = false
      return
    }

    val currentSevPriority = currentSeverity?.ordinal
    if (currentSevPriority != null && currentSevPriority > severity.ordinal) return

    errorPane.text = text
    currentSeverity = severity
    currentLinkActivationListener = linkActivationListener
    background = when (severity) {
      Severity.INFO -> UIUtil.getPanelBackground()
      Severity.ERROR -> JBUI.CurrentTheme.Validator.errorBackgroundColor()
      Severity.WARNING -> JBUI.CurrentTheme.Validator.warningBackgroundColor()
    }
    isVisible = true
  }

  val isEmpty: Boolean
    get() = currentSeverity == null

  enum class Severity {
    INFO, WARNING, ERROR
  }
}
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Cursor
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit

class HtmlErrorPanel : Wrapper() {
  private var currentSeverity: Severity? = null
  private var currentLinkActivationListener: ((HyperlinkEvent) -> Unit)? = null

  private val errorPane = JEditorPane().apply {
    editorKit = UIUtil.getHTMLEditorKit()
    val linkColor = JBUI.CurrentTheme.Link.linkColor()
    //language=CSS
    (editorKit as HTMLEditorKit).styleSheet.addRule("a {color: rgb(${linkColor.red}, ${linkColor.green}, ${linkColor.blue})}")
    addHyperlinkListener { e ->
      if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        currentLinkActivationListener?.invoke(e)
      }
      else {
        cursor = if (e.eventType == HyperlinkEvent.EventType.ENTERED) Cursor(Cursor.HAND_CURSOR)
        else Cursor(Cursor.DEFAULT_CURSOR)
      }
    }
    isEditable = false
    isFocusable = false
    isOpaque = false
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP)
  }

  init {
    setContent(errorPane)
    isOpaque = true
    isVisible = false
  }

  fun setError(errorText: String?) {
    if (errorText == null) {
      currentSeverity = null
      currentLinkActivationListener = null
      errorPane.text = ""
      isVisible = false
    }
    else setError(errorText)
  }

  fun setError(errorText: String, severity: Severity = Severity.ERROR, linkActivationListener: ((HyperlinkEvent) -> Unit)? = null) {
    val currentSevPriority = currentSeverity?.ordinal
    if (currentSevPriority != null && currentSevPriority > severity.ordinal) return

    errorPane.text = errorText
    currentSeverity = severity
    currentLinkActivationListener = linkActivationListener
    background = when (severity) {
      Severity.ERROR -> JBUI.CurrentTheme.Validator.errorBackgroundColor()
      Severity.WARNING -> JBUI.CurrentTheme.Validator.warningBackgroundColor()
    }
    isVisible = true
  }

  enum class Severity {
    WARNING, ERROR
  }
}
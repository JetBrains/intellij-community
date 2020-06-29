// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.ide.BrowserUtil
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.HyperlinkAdapter
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.BR
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.util.getName
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent

object GHHtmlErrorPanel {

  private const val ERROR_ACTION_HREF = "ERROR_ACTION"

  fun create(errorPrefix: String, error: Throwable,
             errorAction: Action? = null,
             horizontalAlignment: Int = SwingConstants.CENTER): JComponent {
    val model = GHImmutableErrorPanelModel(errorPrefix, error, errorAction)
    return create(model, horizontalAlignment)
  }

  fun create(model: GHErrorPanelModel, horizontalAlignment: Int = SwingConstants.CENTER): JComponent {

    val pane = HtmlEditorPane().apply {
      foreground = UIUtil.getErrorForeground()
      isFocusable = true

      removeHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
      addHyperlinkListener(object : HyperlinkAdapter() {
        override fun hyperlinkActivated(e: HyperlinkEvent) {
          if (e.description == ERROR_ACTION_HREF) {
            model.errorAction?.actionPerformed(ActionEvent(this@apply, ActionEvent.ACTION_PERFORMED, "perform"))
          }
          else {
            BrowserUtil.browse(e.description)
          }
        }
      })
      registerKeyboardAction(ActionListener {
        model.errorAction?.actionPerformed(it)
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED)
    }

    Controller(model, pane, horizontalAlignment)
    return pane
  }

  private class Controller(private val model: GHErrorPanelModel,
                           private val pane: HtmlEditorPane,
                           horizontalAlignment: Int) {

    private val alignmentText = when (horizontalAlignment) {
      SwingConstants.LEFT -> "left"
      SwingConstants.RIGHT -> "right"
      else -> "center"
    }

    init {
      model.addAndInvokeChangeEventListener(::update)
    }

    private fun update() {
      val error = model.error
      if (error != null) {
        pane.isVisible = true
        var errorText = paragraph(model.errorPrefix) + paragraph(getLoadingErrorText(error, BR))
        val errorAction = model.errorAction
        if (errorAction != null) {
          //language=HTML
          errorText += BR + paragraph(a(ERROR_ACTION_HREF, errorAction.getName()))
        }
        pane.setBody(errorText)
      }
      else {
        pane.isVisible = false
        pane.setBody("")
      }
      // JDK bug - need to force height recalculation (see JBR-2256)
      pane.setSize(Int.MAX_VALUE / 2, Int.MAX_VALUE / 2)
    }

    //language=HTML
    private fun paragraph(text: String): String = "<p align='$alignmentText'>${text}</p>"

    //language=HTML
    private fun a(link: String, text: String): String = "<a href='$link'>$text</a>"
  }

  private fun getLoadingErrorText(error: Throwable, newLineSeparator: String = "\n"): String {
    if (error is GithubStatusCodeException && error.error != null && error.error!!.message != null) {
      val githubError = error.error!!
      val builder = StringBuilder(githubError.message)
      val errors = githubError.errors
      if (!errors.isNullOrEmpty()) {
        builder.append(": ").append(newLineSeparator)
        for (e in errors) {

          builder.append(e.message ?: GithubBundle.message("gql.error.in.field", e.code, e.resource, e.field.orEmpty()))
            .append(newLineSeparator)
        }
      }
      return builder.toString()
    }

    return error.message?.let { addDotIfNeeded(it) } ?: GithubBundle.message("unknown.loading.error")
  }

  private fun addDotIfNeeded(line: String) = if (line.endsWith('.')) line else "$line."
}
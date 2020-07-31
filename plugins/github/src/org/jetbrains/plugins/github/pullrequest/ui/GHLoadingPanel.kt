// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.ui.LoadingDecorator
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.NotNullFunction
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.ui.frame.ProgressStripe
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.getName
import java.awt.BorderLayout
import java.awt.GridBagLayout
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke

class GHLoadingPanel<T> @Deprecated("Replaced with factory because JBLoadingPanel is not really needed for initial loading",
                                    ReplaceWith("GHLoadingPanelFactory().create()"))
constructor(model: GHLoadingModel,
            content: T,
            parentDisposable: Disposable,
            textBundle: EmptyTextBundle = EmptyTextBundle.Default)
  : JBLoadingPanel(BorderLayout(), createDecorator(parentDisposable))
  where T : JComponent, T : ComponentWithEmptyText {

  companion object {

    private fun createDecorator(parentDisposable: Disposable): NotNullFunction<JPanel, LoadingDecorator> {
      return NotNullFunction<JPanel, LoadingDecorator> {
        object : LoadingDecorator(it, parentDisposable, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
          override fun customizeLoadingLayer(parent: JPanel, text: JLabel, icon: AsyncProcessIcon): NonOpaquePanel {
            parent.layout = GridBagLayout()

            text.text = ApplicationBundle.message("label.loading.page.please.wait")
            text.icon = AnimatedIcon.Default()
            text.foreground = UIUtil.getContextHelpForeground()

            val result = NonOpaquePanel(text)
            parent.add(result)
            return result
          }
        }
      }
    }

    class Controller(private val model: GHLoadingModel,
                     private val loadingPanel: JBLoadingPanel,
                     private val updateLoadingPanel: ProgressStripe,
                     private val statusText: StatusText,
                     private val textBundle: EmptyTextBundle,
                     private val errorHandlerProvider: () -> GHLoadingErrorHandler?) {

      init {
        model.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
          override fun onLoadingStarted() = update()
          override fun onLoadingCompleted() = update()
          override fun onReset() = update()
        })
        update()
      }

      private fun update() {
        if (model.loading) {
          loadingPanel.isFocusable = true
          statusText.clear()
          if (model.resultAvailable) {
            updateLoadingPanel.startLoading()
          }
          else {
            loadingPanel.startLoading()
          }
        }
        else {
          loadingPanel.stopLoading()
          updateLoadingPanel.stopLoading()

          if (model.resultAvailable) {
            loadingPanel.isFocusable = false
            loadingPanel.resetKeyboardActions()
            statusText.text = textBundle.empty
          }
          else {
            val error = model.error
            if (error != null) {
              statusText.clear()
                .appendText(textBundle.errorPrefix, SimpleTextAttributes.ERROR_ATTRIBUTES)
                .appendSecondaryText(error.message ?: GithubBundle.message("unknown.loading.error"),
                                     SimpleTextAttributes.ERROR_ATTRIBUTES,
                                     null)

              errorHandlerProvider()?.getActionForError(error)?.let {
                statusText.appendSecondaryText(" ${it.getName()}", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, it)
                loadingPanel.registerKeyboardAction(it, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED)
              }
            }
            else statusText.text = textBundle.default
          }
        }
      }
    }
  }

  private val updateLoadingPanel =
    ProgressStripe(content, parentDisposable, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS).apply {
      isOpaque = false
    }
  var errorHandler: GHLoadingErrorHandler? = null

  init {
    isOpaque = false

    add(updateLoadingPanel, BorderLayout.CENTER)

    Controller(model, this, updateLoadingPanel, content.emptyText, textBundle) { errorHandler }
  }

  interface EmptyTextBundle {
    val default: String
    val empty: String
    val errorPrefix: String

    class Simple(override val default: String, override val errorPrefix: String, override val empty: String = "") : EmptyTextBundle

    object Default : EmptyTextBundle {
      override val default: String = ""
      override val empty: String = ""
      override val errorPrefix: String = GithubBundle.message("cannot.load.data")
    }
  }
}
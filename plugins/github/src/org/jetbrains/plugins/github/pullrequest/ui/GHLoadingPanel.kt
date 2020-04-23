// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.ui.LoadingDecorator
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.NotNullFunction
import com.intellij.util.ui.*
import com.intellij.vcs.log.ui.frame.ProgressStripe
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.util.getName
import java.awt.BorderLayout
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.event.HyperlinkEvent

class GHLoadingPanel<T> @Deprecated("Replaced with factory method becuse JBLoadingPanel is not really needed for initial loading",
                                    ReplaceWith("GHLoadingPanel.create"))
constructor(model: GHLoadingModel,
            content: T,
            parentDisposable: Disposable,
            textBundle: EmptyTextBundle = EmptyTextBundle.Default)
  : JBLoadingPanel(BorderLayout(), createDecorator(parentDisposable))
  where T : JComponent, T : ComponentWithEmptyText {

  companion object {

    fun create(model: GHLoadingModel,
               contentFactory: (JPanel) -> JComponent,
               parentDisposable: Disposable,
               @Nls(capitalization = Nls.Capitalization.Sentence) errorPrefix: String = GithubBundle.message("cannot.load.data"),
               errorHandler: GHLoadingErrorHandler? = null): JComponent {

      val panel = NonOpaquePanel()
      ContentController(model, panel,
                        contentFactory, parentDisposable,
                        errorPrefix, errorHandler)
      return panel
    }

    private class ContentController(private val model: GHLoadingModel, private val panel: Wrapper,
                                    contentFactory: (JPanel) -> JComponent, parentDisposable: Disposable,
                                    private val errorPrefix: String,
                                    private val errorHandler: GHLoadingErrorHandler?) {

      private var lastResultAvailable = false
      private val contentProgressStripe by lazy(LazyThreadSafetyMode.NONE) {
        ProgressStripe(contentFactory(panel), parentDisposable, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS).apply {
          isOpaque = false
        }
      }

      init {
        model.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
          override fun onLoadingStarted() = update()
          override fun onLoadingCompleted() = update()
        })
        update()
      }

      private fun update() {
        if (model.resultAvailable) {
          if (model.loading) contentProgressStripe.startLoadingImmediately() else contentProgressStripe.stopLoading()
        }

        if (lastResultAvailable == model.resultAvailable && model.resultAvailable) return

        val content = when {
          model.resultAvailable -> contentProgressStripe
          model.loading -> createLoadingLabelPanel()
          model.error != null -> createErrorPanel(model.error!!)
          else -> null
        }
        panel.setContent(content)
        panel.repaint()
        lastResultAvailable = model.resultAvailable
      }

      private fun createErrorPanel(error: Throwable): JComponent {
        return JPanel(SingleComponentCenteringLayout()).apply {
          isOpaque = false
          border = JBUI.Borders.empty(8)

          val errorAction = errorHandler?.getActionForError(error)
          //language=HTML
          val errorDescription = "<p align='center'>$errorPrefix</p><p align='center'>${error.message}<p/>"
          val body = if (errorAction == null) errorDescription
          else {
            //language=HTML
            errorDescription + "<br/><p align='center'><a href=''>${errorAction.getName()}</a><p/>"
          }

          add(HtmlEditorPane(body).apply {
            foreground = UIUtil.getErrorForeground()

            if (errorAction != null) {
              registerKeyboardAction(errorAction, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED)
              removeHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
              addHyperlinkListener(object : HyperlinkAdapter() {
                override fun hyperlinkActivated(e: HyperlinkEvent?) {
                  errorAction.actionPerformed(ActionEvent(this@apply, ActionEvent.ACTION_PERFORMED, "perform"))
                }
              })
            }
          })
        }
      }

      private fun createLoadingLabelPanel() = JPanel(SingleComponentCenteringLayout()).apply {
        isOpaque = false
        add(JLabel().apply {
          foreground = UIUtil.getContextHelpForeground()
          text = "Loading..."
          icon = AnimatedIcon.Default()
        })
      }
    }

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
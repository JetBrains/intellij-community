// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.ui.frame.ProgressStripe
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.GHHtmlErrorPanel
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class GHLoadingPanelFactory<T>(private val model: GHSimpleLoadingModel<T>,
                               @Nls(capitalization = Nls.Capitalization.Sentence) private val notLoadingText: String? = null,
                               @Nls(capitalization = Nls.Capitalization.Sentence) private val errorPrefix: String =
                                 GithubBundle.message("cannot.load.data"),
                               private val errorHandler: GHLoadingErrorHandler? = null) {

  private val contentListeners = mutableListOf<(JComponent) -> Unit>()

  fun withContentListener(listener: (JComponent) -> Unit): GHLoadingPanelFactory<T> {
    contentListeners.add(listener)
    return this
  }

  fun createWithUpdatesStripe(parentDisposable: Disposable, contentFactory: (JPanel, SingleValueModel<T>) -> JComponent): JComponent {
    val panel = NonOpaquePanel()
    object : ContentController<T>(model, panel, notLoadingText, errorPrefix, errorHandler, contentListeners.toList()) {
      override fun createContent(parentPanel: JPanel, valueModel: SingleValueModel<T>): JComponent {
        val stripe = ProgressStripe(contentFactory(parentPanel, valueModel), parentDisposable,
                                    ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS).apply {
          isOpaque = false
        }

        fun updateStripe() {
          if (model.resultAvailable) {
            if (model.loading) stripe.startLoadingImmediately() else stripe.stopLoading()
          }
          else stripe.stopLoading()
        }
        model.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
          override fun onLoadingStarted() = updateStripe()
          override fun onLoadingCompleted() = updateStripe()
        })
        updateStripe()
        return stripe
      }
    }
    return panel
  }

  fun create(contentFactory: (JPanel, T) -> JComponent): JComponent {
    val panel = NonOpaquePanel()
    object : ContentController<T>(model, panel, notLoadingText, errorPrefix, errorHandler, contentListeners.toList()) {
      override fun createContent(parentPanel: JPanel, valueModel: SingleValueModel<T>) =
        contentFactory(parentPanel, valueModel.value)
    }
    return panel
  }

  fun createWithModel(contentFactory: (JPanel, SingleValueModel<T>) -> JComponent): JComponent {
    val panel = NonOpaquePanel()
    object : ContentController<T>(model, panel, notLoadingText, errorPrefix, errorHandler, contentListeners.toList()) {
      override fun createContent(parentPanel: JPanel, valueModel: SingleValueModel<T>) = contentFactory(parentPanel, valueModel)
    }
    return panel
  }

  companion object {
    private abstract class ContentController<T>(private val model: GHSimpleLoadingModel<T>, private val panel: Wrapper,
                                                private val notLoadingText: String?,
                                                private val errorPrefix: String,
                                                private val errorHandler: GHLoadingErrorHandler?,
                                                private val contentListeners: List<(JComponent) -> Unit>) {

      private var valueModel: SingleValueModel<T>? = null
      private var content: JComponent? = null

      init {
        model.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
          override fun onLoadingStarted() = update()
          override fun onLoadingCompleted() = update()
        })
        update()
      }

      private fun update() {
        if (model.resultAvailable) {
          @Suppress("UNCHECKED_CAST")
          val model = getValueModel(model.result as T)
          val content = getContent(model)
          if (panel.targetComponent !== content) {
            panel.setContent(content)
            contentListeners.forEach { it(panel.targetComponent) }
            panel.repaint()
          }
        }
        else {
          val content = when {
            model.loading -> createLoadingLabelPanel()
            model.error != null -> createErrorPanel(model.error!!)
            else -> createEmptyContent()
          }
          panel.setContent(content)
          contentListeners.forEach { it(panel.targetComponent) }
          panel.repaint()
        }
      }

      private fun getValueModel(result: T): SingleValueModel<T> {
        var current = valueModel
        if (current != null) {
          current.value = result
          return current
        }
        current = SingleValueModel(result)
        valueModel = current
        return current
      }

      private fun getContent(valueModel: SingleValueModel<T>): JComponent {
        var current = content
        if (current != null) return current
        current = createContent(panel, valueModel)
        content = current
        return current
      }

      abstract fun createContent(parentPanel: JPanel, valueModel: SingleValueModel<T>): JComponent

      private fun createEmptyContent(): JComponent? {
        if (notLoadingText == null) return null

        val pane = HtmlEditorPane(notLoadingText).apply {
          foreground = UIUtil.getContextHelpForeground()
        }
        return JPanel(SingleComponentCenteringLayout()).apply {
          isOpaque = false
          add(pane)
        }
      }

      private fun createErrorPanel(error: Throwable): JComponent {
        return JPanel(SingleComponentCenteringLayout()).apply {
          isOpaque = false
          border = JBUI.Borders.empty(8)

          add(GHHtmlErrorPanel.create(errorPrefix, error, errorHandler?.getActionForError(error)))
        }
      }

      private fun createLoadingLabelPanel() = JPanel(SingleComponentCenteringLayout()).apply {
        isOpaque = false
        add(JLabel().apply {
          foreground = UIUtil.getContextHelpForeground()
          text = ApplicationBundle.message("label.loading.page.please.wait")
          icon = AnimatedIcon.Default()
        })
      }
    }
  }
}
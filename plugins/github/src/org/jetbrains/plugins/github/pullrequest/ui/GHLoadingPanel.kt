// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.vcs.log.ui.frame.ProgressStripe
import java.awt.BorderLayout
import javax.swing.JComponent

class GHLoadingPanel<T>(private val model: GHLoadingModel<*>,
                        private val content: T,
                        parentDisposable: Disposable,
                        private val textBundle: EmptyTextBundle = EmptyTextBundle.Default)
  : JBLoadingPanel(BorderLayout(), parentDisposable, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)
  where T : JComponent, T : ComponentWithEmptyText {

  private val updateLoadingPanel =
    ProgressStripe(content, parentDisposable, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS).apply {
      isOpaque = false
    }

  init {
    isOpaque = false

    add(updateLoadingPanel, BorderLayout.CENTER)

    model.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
      override fun onLoadingStarted() = update()
      override fun onLoadingCompleted() = update()
      override fun onReset() = update()
    })
    update()
  }

  private fun update() {
    if (model.loading) {
      isFocusable = true
      content.emptyText.clear()
      if (model.result != null) {
        updateLoadingPanel.startLoading()
      }
      else {
        startLoading()
      }
    }
    else {
      isFocusable = false
      stopLoading()
      updateLoadingPanel.stopLoading()

      val error = model.error
      when {
        error != null -> content.emptyText.clear()
          .appendText(textBundle.errorPrefix, SimpleTextAttributes.ERROR_ATTRIBUTES)
          .appendSecondaryText(error.message ?: "Unknown error", SimpleTextAttributes.ERROR_ATTRIBUTES, null)
        model.result != null -> content.emptyText.text = textBundle.empty
        else -> content.emptyText.text = textBundle.default
      }
    }
  }

  interface EmptyTextBundle {
    val default: String
    val empty: String
    val errorPrefix: String

    class Simple(override val default: String, override val errorPrefix: String, override val empty: String = "") : EmptyTextBundle

    object Default : EmptyTextBundle {
      override val default: String = ""
      override val empty: String = ""
      override val errorPrefix: String = "Can't load data"
    }
  }
}
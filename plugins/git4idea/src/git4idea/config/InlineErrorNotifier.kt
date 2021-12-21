// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.ide.plugins.newui.TwoLineProgressIndicator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.concurrency.EdtScheduledExecutorService
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nls.Capitalization.Sentence
import org.jetbrains.annotations.Nls.Capitalization.Title
import org.jetbrains.annotations.NotNull
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.SwingConstants

internal interface InlineComponent {
  fun showProgress(@Nls(capitalization = Title) text: String): ProgressIndicator
  fun showError(@Nls(capitalization = Sentence) errorText: String, link: LinkLabel<*>? = null)
  fun showMessage(@Nls(capitalization = Sentence) text: String)
  fun hideProgress()
}

internal open class InlineErrorNotifier(private val inlineComponent: InlineComponent,
                                        private val modalityState: ModalityState,
                                        private val disposable: Disposable) : ErrorNotifier {

  var isTaskInProgress: Boolean = false // Check from EDT only
    private set

  override fun showError(@Nls(capitalization = Sentence) text: String,
                         @Nls(capitalization = Sentence) description: String?,
                         fixOption: ErrorNotifier.FixOption?) {
    invokeAndWaitIfNeeded(modalityState) {
      val linkLabel = fixOption?.let {
        LinkLabel<Any>(fixOption.text, null) { _, _ ->
          fixOption.fix()
        }
      }

      val message = HtmlBuilder().appendRaw(text)
      if (description != null) {
        message.br().appendRaw(description)
      }
      message.wrapWithHtmlBody()

      inlineComponent.showError(message.toString(), linkLabel)
    }
  }

  override fun showError(@Nls(capitalization = Sentence) text: String) {
    invokeAndWaitIfNeeded(modalityState) {
      inlineComponent.showError(text)
    }
  }

  @RequiresEdt
  override fun executeTask(@Nls(capitalization = Title) title: String, cancellable: Boolean, action: () -> Unit) {
    val pi = inlineComponent.showProgress(title)
    isTaskInProgress = true
    Disposer.register(disposable, Disposable { pi.cancel() })
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        ProgressManager.getInstance().runProcess(Runnable {
          action()
        }, pi)
      }
      finally {
        invokeAndWaitIfNeeded(modalityState) {
          isTaskInProgress = false
        }
      }
    }
  }

  override fun changeProgressTitle(@Nls(capitalization = Title) text: String) {
    invokeAndWaitIfNeeded(modalityState) {
      inlineComponent.showProgress(text)
    }
  }

  override fun showMessage(@NlsContexts.NotificationContent @NotNull message: String) {
    invokeAndWaitIfNeeded(modalityState) {
      inlineComponent.showMessage(message)
    }
  }

  override fun hideProgress() {
    invokeAndWaitIfNeeded(modalityState) {
      inlineComponent.hideProgress()
    }
  }
}

class GitExecutableInlineComponent(private val container: BorderLayoutPanel,
                                   private val modalityState: ModalityState,
                                   private val panelToValidate: JPanel?) : InlineComponent {

  private var progressShown = false

  override fun showProgress(@Nls(capitalization = Title) text: String): ProgressIndicator {
    container.removeAll()

    val pi = TwoLineProgressIndicator(true).apply {
      this.text = text
    }

    progressShown = true
    EdtScheduledExecutorService.getInstance().schedule({
      if (progressShown) {
        container.addToLeft(pi.component)
        panelToValidate?.validate()
      }
    }, modalityState, DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS.toLong(), TimeUnit.MILLISECONDS)

    return pi
  }

  override fun showError(@Nls(capitalization = Sentence) errorText: String, link: LinkLabel<*>?) {
    container.removeAll()
    progressShown = false

    val label = JBLabel(errorText)
      .setCopyable(true)
      .setAllowAutoWrapping(true)
      .apply {
        foreground = UIUtil.getErrorForeground()
      }

    container.addToCenter(label)
    if (link != null) {
      link.verticalAlignment = SwingConstants.TOP
      container.addToRight(link)
    }
    panelToValidate?.validate()
  }

  override fun showMessage(@Nls(capitalization = Sentence) text: @NlsContexts.Label String) {
    container.removeAll()
    progressShown = false

    val label = JBLabel(text)
      .setCopyable(true)

    container.addToLeft(label)
    panelToValidate?.validate()
  }

  override fun hideProgress() {
    container.removeAll()
    progressShown = false

    panelToValidate?.validate()
  }
}

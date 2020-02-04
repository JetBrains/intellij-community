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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.Alarm.ThreadToUse.SWING_THREAD
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

internal interface InlineComponent {
  fun showProgress(text: String): ProgressIndicator
  fun showError(errorText: String, link: LinkLabel<*>? = null)
  fun showMessage(text: String)
  fun hideProgress()
}

internal open class InlineErrorNotifier(private val inlineComponent: InlineComponent,
                                        private val modalityState: ModalityState,
                                        private val disposable: Disposable) : ErrorNotifier {

  var isTaskInProgress: Boolean = false // Check from EDT only
    private set

  override fun showError(text: String, description: String?, fixOption: ErrorNotifier.FixOption) {
    invokeAndWaitIfNeeded(modalityState) {
      val linkLabel = LinkLabel<Any>(fixOption.text, null) { _, _ ->
        fixOption.fix()
      }
      val message = if (description == null) text else "$text\n$description"
      inlineComponent.showError(message, linkLabel)
    }
  }

  override fun showError(text: String) {
    invokeAndWaitIfNeeded(modalityState) {
      inlineComponent.showError(text)
    }
  }

  @CalledInAwt
  override fun executeTask(title: String, cancellable: Boolean, action: () -> Unit) {
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

  override fun changeProgressTitle(text: String) {
    invokeAndWaitIfNeeded(modalityState) {
      inlineComponent.showProgress(text)
    }
  }

  override fun showMessage(text: String) {
    invokeAndWaitIfNeeded(modalityState) {
      inlineComponent.showMessage(text)
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

  override fun showProgress(@Nls text: String): ProgressIndicator {
    container.removeAll()

    val pi = TwoLineProgressIndicator(true).apply {
      this.text = text
    }

    progressShown = true
    SingleAlarm(Runnable {
      if (progressShown) {
        container.addToLeft(pi.component)
        panelToValidate?.validate()
      }
    }, delay = DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS, threadToUse = SWING_THREAD).request(modalityState)

    return pi
  }

  override fun showError(errorText: String, link: LinkLabel<*>?) {
    container.removeAll()
    progressShown = false

    val label = multilineLabel(errorText).apply {
      foreground = DialogWrapper.ERROR_FOREGROUND_COLOR
    }

    container.addToCenter(label)
    if (link != null) {
      link.verticalAlignment = SwingConstants.TOP
      container.addToRight(link)
    }
    panelToValidate?.validate()
  }

  override fun showMessage(text: String) {
    container.removeAll()
    progressShown = false

    container.addToLeft(JBLabel(text))
    panelToValidate?.validate()
  }

  override fun hideProgress() {
    container.removeAll()
    progressShown = false

    panelToValidate?.validate()
  }

  private fun multilineLabel(text: String): JComponent = JBLabel(text).apply {
    setAllowAutoWrapping(true)
    setCopyable(true)
  }
}

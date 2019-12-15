// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.ui.components.labels.LinkLabel
import org.jetbrains.annotations.CalledInAwt

internal interface InlineComponent {
  fun showProgress(text: String)
  fun showError(errorText: String, link: LinkLabel<*>? = null)
  fun showMessage(text: String)
  fun hideProgress()
}

internal class InlineErrorNotifier(private val inlineComponent: InlineComponent,
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
    inlineComponent.showProgress(title)
    isTaskInProgress = true
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        BackgroundTaskUtil.runUnderDisposeAwareIndicator(disposable, Runnable {
          action()
        })
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
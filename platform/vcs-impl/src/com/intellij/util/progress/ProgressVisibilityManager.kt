// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.progress

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.CoreProgressManager
import org.jetbrains.annotations.CalledInAwt

/**
 * Allows to run multiple processes with a single UI indicator
 */
abstract class ProgressVisibilityManager : Disposable {
  private val indicators: MutableList<ProgressIndicator> = ArrayList()
  var disposed = false
    private set

  @CalledInAwt
  fun run(task: Task.Backgroundable): ProgressIndicator {
    val indicator = EmptyProgressIndicator(getModalityState())
    indicators.add(indicator)
    setProgressVisible(true)
    (ProgressManager.getInstance() as CoreProgressManager).runProcessWithProgressAsynchronously(task, indicator) {
      runInEdt(indicator.modalityState) {
        indicators.remove(indicator)
        setProgressVisible(indicators.isNotEmpty())
      }
    }
    return indicator
  }

  override fun dispose() {
    for (indicator in indicators) {
      indicator.cancel()
    }
    disposed = true
  }

  protected abstract fun setProgressVisible(visible: Boolean)

  protected abstract fun getModalityState(): ModalityState
}
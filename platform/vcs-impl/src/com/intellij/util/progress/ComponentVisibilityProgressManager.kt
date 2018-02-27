// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.progress

import com.intellij.openapi.application.ModalityState
import javax.swing.JComponent

/**
 * Shows [progressDisplayComponent] when there's at least one running task and hides it when there are none
 */
class ComponentVisibilityProgressManager(private val progressDisplayComponent: JComponent) : ProgressVisibilityManager() {
  override fun getModalityState(): ModalityState {
    return ModalityState.stateForComponent(progressDisplayComponent)
  }

  override fun setProgressVisible(visible: Boolean) {
    progressDisplayComponent.isVisible = visible
  }
}
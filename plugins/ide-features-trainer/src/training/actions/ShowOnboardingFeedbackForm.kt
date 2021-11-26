// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import training.ui.showOnboardingLessonFeedbackForm

private class ShowOnboardingFeedbackForm : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    showOnboardingLessonFeedbackForm(e.project, null, false)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = true
  }
}
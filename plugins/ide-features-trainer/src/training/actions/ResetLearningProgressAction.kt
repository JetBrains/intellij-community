// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.showYesNoDialog
import training.learn.LearnBundle
import training.util.clearTrainingProgress

private class ResetLearningProgressAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    if (showYesNoDialog(LearnBundle.message("learn.option.reset.progress.dialog"),
                        LearnBundle.message("learn.option.reset.progress.confirm"), null)) {
      clearTrainingProgress()
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.showYesNoDialog
import training.learn.LearnBundle
import training.util.clearTrainingProgress

class ResetLearningProgressAction : AnAction(AllIcons.Actions.Restart) {
  override fun actionPerformed(e: AnActionEvent) {
    if (showYesNoDialog(LearnBundle.message("learn.option.reset.progress.dialog"), LearnBundle.message("learn.option.reset.progress.confirm"), null)) {
      clearTrainingProgress()
    }
  }
}

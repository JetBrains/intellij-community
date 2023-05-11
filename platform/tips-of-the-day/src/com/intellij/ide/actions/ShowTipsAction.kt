// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.TipsOfTheDayUsagesCollector
import com.intellij.ide.util.TipAndTrickManager.Companion.getInstance
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.project.DumbAware
import kotlinx.coroutines.launch

private class ShowTipsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    @Suppress("DEPRECATION")
    (project?.coroutineScope ?: ApplicationManager.getApplication().coroutineScope).launch {
      withBackgroundProgress(project!!, IdeBundle.message("tip.of.the.day.progress.title"), TaskCancellation.nonCancellable()) {
        TipsOfTheDayUsagesCollector.triggerDialogShown(TipsOfTheDayUsagesCollector.DialogType.manually)
        getInstance().showTipDialog(project)
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.TipsOfTheDayUsagesCollector
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private class ShowTipsAction : AnAction(), DumbAware, ActionRemoteBehaviorSpecification {
  override fun getBehavior(): ActionRemoteBehavior = ActionRemoteBehavior.BackendOnly

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    ((project ?: ApplicationManager.getApplication()) as ComponentManagerEx).getCoroutineScope().launch {
      val openTipsDialog: suspend CoroutineScope.() -> Unit = {
        TipsOfTheDayUsagesCollector.triggerDialogShown(TipsOfTheDayUsagesCollector.DialogType.manually)
        TipAndTrickManager.getInstance().showTipDialog(project)
      }
      if (project != null) {
        withBackgroundProgress(project, IdeBundle.message("tip.of.the.day.progress.title"),
                               TaskCancellation.nonCancellable(), openTipsDialog)
      }
      else openTipsDialog()  // invoked from Welcome Screen
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
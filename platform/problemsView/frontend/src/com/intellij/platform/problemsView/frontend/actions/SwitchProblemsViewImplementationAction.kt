// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend.actions

import com.intellij.analysis.problemsView.toolWindow.splitApi.isSplitProblemsViewKeyEnabled
import com.intellij.analysis.problemsView.toolWindow.splitApi.setProblemsViewImplementationForNextIdeRun
import com.intellij.ide.IdeBundle
import com.intellij.idea.ActionsBundle

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.problemsView.shared.ProblemsViewApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SwitchProblemsViewImplementationAction : DumbAwareToggleAction(){

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = IdeProductMode.isFrontend
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return isSplitProblemsViewKeyEnabled()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    e.coroutineScope.launch {
      val app = ApplicationManagerEx.getApplicationEx()
      val restartAllowed = withContext(Dispatchers.EDT) {
        if (app == null) {
          thisLogger().warn("Application is null, abort restart")
          return@withContext false
        }
        val standardRestartDialogText = IdeBundle.message(if (app.isRestartCapable()) "dialog.message.restart.ide" else "dialog.message.restart.alt")
        val answer = Messages.showYesNoDialog(
          standardRestartDialogText,
          IdeBundle.message("dialog.title.restart.ide"),
          IdeBundle.message(if (app.isRestartCapable()) "ide.restart.action" else "ide.shutdown.action"),
          ActionsBundle.message("action.ServiceView.SwitchImplementation.restart.cancellation.text"),
          Messages.getQuestionIcon()
        )

        return@withContext answer == Messages.YES
      }

      if (!restartAllowed) return@launch

      setProblemsViewImplementationForNextIdeRun(state)
      ProblemsViewApi.getInstance().changeProblemsViewImplementationForNextIdeRunAndRestart(state)
    }
  }
}

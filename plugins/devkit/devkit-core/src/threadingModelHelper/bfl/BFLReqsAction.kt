// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper.bfl

import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbService
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.concurrent.Callable


class BFLReqsAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    currentThreadCoroutineScope().launch(Dispatchers.Default) {
      val project = e.project ?: return@launch
      DumbService.getInstance(project)
      readAction {
        ProgressManager.checkCanceled()
      }
      val results = ReadAction.nonBlocking(Callable {
        val checker = BFLReqsChecker()
        checker.runChecker(project)
      })
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
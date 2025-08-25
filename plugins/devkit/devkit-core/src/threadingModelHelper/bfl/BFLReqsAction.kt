// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper.bfl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.util.application
import java.util.concurrent.Callable


class BFLReqsAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    ApplicationManager.getApplication().executeOnPooledThread {
      val project = e.project ?: return@executeOnPooledThread
      DumbService.getInstance(project).waitForSmartMode()
      val results = ReadAction.nonBlocking(Callable {
        val checker = BFLReqsChecker()
        checker.runChecker(project)
      }).executeSynchronously()
      val report = buildString {
        for (result in results) {
          if (result.canMoveToBackground)
            appendLine(result.implementation.qualifiedName)
        }
      }
      application.invokeLater {
        println(report)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
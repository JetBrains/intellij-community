// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.core

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class LongBackgroundWriteAction: AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.coroutineScope.launch(Dispatchers.Default) {
      val project = e.project
      if (project != null) {
        withBackgroundProgress(project, DevCoreBundle.message("progress.title.long.write.action")) {
         doRunLongBackgroundWriteAction()
        }
      } else {
        doRunLongBackgroundWriteAction()
      }
    }
  }

  private suspend fun doRunLongBackgroundWriteAction() {
    backgroundWriteAction {
      Thread.sleep(10_000)
    }
  }
}
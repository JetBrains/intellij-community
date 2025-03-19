// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

//Just to get coroutineScope
@ApiStatus.Internal
@Service
class CheckVFSHealthService(val coroutineScope: CoroutineScope)


internal class CheckVFSHealthAction : AnAction(ActionsBundle.message("action.CheckVfsSanity.text")) {
  override fun actionPerformed(e: AnActionEvent) {

    service<CheckVFSHealthService>().coroutineScope.launch(Dispatchers.IO) {
      //Use modal dialog to prevent calling more than once:
      withModalProgress(ModalTaskOwner.guess(),
                        ActionsBundle.message("action.CheckVfsSanity.progress"),
                        TaskCancellation.nonCancellable()) {
        val checker = VFSHealthChecker(FSRecords.getInstance(), FSRecords.LOG)
        checker.checkHealth(checkForOrphanRecords = true)
      }
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
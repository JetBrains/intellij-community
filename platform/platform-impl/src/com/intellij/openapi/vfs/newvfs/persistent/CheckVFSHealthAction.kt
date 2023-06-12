// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.withModalProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class CheckVFSHealthAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val cs = CoroutineScope(SupervisorJob())
    cs.launch {
      //withBackgroundProgress(project, ActionsBundle.message("action.CheckVfsSanity.progress"), cancellable = false) {
      //Use modal dialog to prevent calling more than once:
      withModalProgress(ModalTaskOwner.guess(), ActionsBundle.message("action.CheckVfsSanity.progress"), TaskCancellation.nonCancellable() ){
        val checker = VFSHealthChecker(FSRecords.implOrFail(), FSRecords.LOG)
        val report = checker.checkHealth()
        FSRecords.LOG.info("VFS health check report: $report")

        //run an old version still -- to compare:
        try {
          FSRecords.checkSanity()
        }
        catch (t: Throwable){
          FSRecords.LOG.warn("VFS health check: old sanity-checking version", t);
        }
      }
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
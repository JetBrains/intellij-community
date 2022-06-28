// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.data.VcsLogStorageImpl
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.util.StorageId
import com.intellij.vcs.log.util.VcsLogUtil
import org.jetbrains.annotations.Nls
import java.util.concurrent.ExecutionException

class InvalidateVcsLogCaches : DumbAwareAction(actionText(VcsLogBundle.message("vcs"))) {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val logManager = VcsProjectLog.getInstance(project).logManager
    if (logManager == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isVisible = true
    e.presentation.isEnabled = logManager.storageIds().isNotEmpty()
    e.presentation.text = actionText(VcsLogUtil.getVcsDisplayName(project, logManager))
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val logManager = VcsProjectLog.getInstance(project).logManager
    val storageIds = logManager?.storageIds()
    if (storageIds.isNullOrEmpty()) return

    val vcsName = VcsLogUtil.getVcsDisplayName(project, logManager)

    val invalidateCachesFuture = VcsProjectLog.getInstance(project).runOnDisposedLog {
      for (storageId in storageIds) {
        try {
          val storageDir = storageId.projectStorageDir
          val deleted = FileUtil.deleteWithRenaming(storageDir)
          if (deleted) thisLogger().info("Deleted $storageDir")
          else thisLogger().error("Could not delete $storageDir")
        }
        catch (t: Throwable) {
          thisLogger().error(t)
        }
      }
    } ?: return
    ProgressManager.getInstance().runProcessWithProgressSynchronously(Runnable {
      try {
        invalidateCachesFuture.get()
      }
      catch (_: InterruptedException) {
      }
      catch (e: ExecutionException) {
        thisLogger().error(e)
      }
    }, VcsLogBundle.message("vcs.log.invalidate.caches.progress", vcsName), false, project)
  }

  companion object {
    private fun actionText(vcsName: String): @Nls String {
      return VcsLogBundle.message("vcs.log.invalidate.caches.text", vcsName)
    }

    private fun VcsLogManager.storageIds(): List<StorageId> {
      return listOfNotNull((dataManager.index as? VcsLogPersistentIndex)?.indexStorageId,
                           (dataManager.storage as? VcsLogStorageImpl)?.refsStorageId,
                           (dataManager.storage as? VcsLogStorageImpl)?.hashesStorageId)
    }
  }
}
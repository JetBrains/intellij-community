// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.configurationStore.saveSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<ChangeListManagerRefreshHelper>()

@ApiStatus.Experimental
object ChangeListManagerRefreshHelper {
  fun requestRefresh(project: Project) {
    project.service<Refresher>().requestRefresh()
  }

  /**
   * Launches a refresh of the project changes or displays or notification if changes refresh is not possible due to some activity
   */
  fun launchRefreshOrNotifyFrozen(project: Project) {
    project.service<Refresher>().launchRefreshOrNotifyFrozen()
  }

  private fun invokeCustomRefreshes(project: Project) {
    for (refresher in ChangesViewRefresher.EP_NAME.getExtensionList(project)) {
      refresher.refresh(project)
    }
  }

  @CalledInAny
  private inline fun doRefreshAndReportMetrics(project: Project, refreshAction: () -> Unit) {
    LOG.debug("Performing changes refresh")

    val changeListManager = ChangeListManagerEx.getInstanceEx(project)
    val changesBeforeUpdate = changeListManager.getAllChanges()
    val unversionedBefore = changeListManager.getUnversionedFilesPaths()
    val wasUpdatingBefore = changeListManager.isInUpdate()

    refreshAction()

    VcsDirtyScopeManager.getInstance(project).markEverythingDirty()

    changeListManager.invokeAfterUpdate(false) {
      val changesAfterUpdate = changeListManager.getAllChanges()
      val unversionedAfter = changeListManager.getUnversionedFilesPaths()
      VcsStatisticsCollector
        .logRefreshActionPerformed(project, changesBeforeUpdate, changesAfterUpdate, unversionedBefore, unversionedAfter,
                                   wasUpdatingBefore)
    }
  }

  @Service(Service.Level.PROJECT)
  private class Refresher(private val project: Project, private val cs: CoroutineScope) {
    private val sem = OverflowSemaphore(overflow = BufferOverflow.DROP_OLDEST)

    private val refreshRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val debouncedRefresherJob = cs.launch(start = CoroutineStart.LAZY) {
      @OptIn(FlowPreview::class)
      refreshRequests.debounce(300.milliseconds).collect {
        refresh(false)
      }
    }

    fun requestRefresh() {
      debouncedRefresherJob.start()
      refreshRequests.tryEmit(Unit)
    }

    fun launchRefreshOrNotifyFrozen() {
      cs.launch {
        refresh(true)
      }
    }

    private suspend fun refresh(notifyFrozen: Boolean) {
      if (project.isDisposed()) return
      val clm = ChangeListManager.getInstance(project)
      val clmFrozen = if (notifyFrozen) {
        withContext(Dispatchers.EDT) {
          clm.isFreezedWithNotification(null)
        }
      }
      else {
        clm.isFreezed != null
      }
      if (clmFrozen) return

      sem.withPermit {
        withContext(Dispatchers.Default) {
          doRefreshAndReportMetrics(project) {
            LOG.info("Saving all documents and project settings")
            withContext(Dispatchers.EDT) {
              FileDocumentManager.getInstance().saveAllDocuments()
            }
            checkCanceled()
            saveSettings(project)
            checkCanceled()
            invokeCustomRefreshes(project)
          }
        }
      }
    }
  }
}

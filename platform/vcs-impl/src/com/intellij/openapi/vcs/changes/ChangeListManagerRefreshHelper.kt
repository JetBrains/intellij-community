// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.configurationStore.StoreUtil
import com.intellij.configurationStore.saveSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector
import com.intellij.util.concurrency.annotations.RequiresEdt
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

  private suspend fun refresh(project: Project) {
    checkCanceled()
    if (project.isDisposed()) return
    if (ChangeListManager.getInstance(project).isFreezed != null) return

    withContext(Dispatchers.Default) {
      doRefreshAndReportMetrics(project) {
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

  @JvmStatic
  @RequiresEdt
  fun refreshSync(project: Project) {
    if (project.isDisposed()) return
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return

    doRefreshAndReportMetrics(project) {
      LOG.info("Saving all documents and project settings")
      StoreUtil.saveDocumentsAndProjectSettings(project)
      invokeCustomRefreshes(project)
    }
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
  private class Refresher(private val project: Project, cs: CoroutineScope) {
    private val refreshFlow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
      cs.launch {
        @OptIn(FlowPreview::class)
        refreshFlow.debounce(300.milliseconds).collect {
          refresh(project)
        }
      }
    }

    fun requestRefresh() {
      refreshFlow.tryEmit(Unit)
    }
  }
}

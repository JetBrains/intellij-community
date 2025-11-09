// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangesViewModifier.ChangesViewModifierListener
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.vcs.impl.shared.SingleTaskRunner
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewSettings
import com.intellij.platform.vcs.impl.shared.changes.PartialChangesHolder
import com.intellij.platform.vcs.impl.shared.telemetry.ChangesView
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.util.application
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import java.lang.Runnable
import kotlin.time.Duration.Companion.milliseconds

private val REFRESH_DELAY = 100.milliseconds

@ApiStatus.Internal
abstract class CommitChangesViewWithToolbarPanel(
  changesView: ChangesListView,
  protected val cs: CoroutineScope,
) : ChangesViewPanel(changesView) {
  val project: Project get() = changesView.project
  private val settings get() = ChangesViewSettings.getInstance(project)

  private val refresher = SingleTaskRunner(cs) {
    refreshView()
  }

  init {
    refresher.start()
    cs.launch(Dispatchers.UI) {
      refresher.getIdleFlow().collect { idle ->
        changesView.setPaintBusy(!idle)
      }
    }
  }

  @RequiresEdt
  open fun initPanel() {
    cs.launch(Dispatchers.UI) {
      ChangeListsViewModel.getInstance(project).changeListManagerState.collectLatest {
        changesView.repaint()
      }
    }

    cs.launch(Dispatchers.UI) {
      PartialChangesHolder.getInstance(project).updates.collectLatest {
        changesView.repaint()
      }
    }

    changesView.installPopupHandler(
      ActionManager.getInstance().getAction("ChangesViewPopupMenuShared") as ActionGroup
    )

    ChangesTree.installGroupingSupport(changesView.groupingSupport,
                                       settings::groupingKeys,
                                       { settings.groupingKeys = it },
                                       Runnable { scheduleRefresh() })

    ChangesViewModifier.KEY.addChangeListener(project, { resetViewImmediatelyAndRefreshLater() }, cs.asDisposable())
    project.messageBus.connect(cs).subscribe(ChangesViewModifier.TOPIC, ChangesViewModifierListener { scheduleRefresh() })

    scheduleRefresh()
  }

  @CalledInAny
  fun scheduleRefresh() {
    scheduleRefresh(withDelay = true)
  }

  @CalledInAny
  fun scheduleRefreshNow(@RequiresBackgroundThread callback: Runnable? = null) {
    scheduleRefresh(withDelay = false, callback = callback)
  }

  @RequiresEdt
  fun setGrouping(groupingKey: String) {
    changesView.groupingSupport.setGroupingKeysOrSkip(setOf(groupingKey))
    scheduleRefreshNow()
  }

  @CalledInAny
  protected fun scheduleRefresh(withDelay: Boolean, @RequiresBackgroundThread callback: Runnable? = null) {
    if (!withDelay && callback == null) {
      refresher.request()
      return
    }

    cs.launch {
      if (withDelay) delay(REFRESH_DELAY)
      refresher.request()
      refresher.awaitNotBusy()
      callback?.run()
    }
  }

  @RequiresBackgroundThread
  private suspend fun refreshView() {
    if (!cs.isActive || !project.isInitialized || application.isUnitTestMode) return
    val (modelData, model) = TRACER.spanBuilder(ChangesView.ChangesViewRefreshBackground.name).use {
      val modelData = getModelData()

      modelData to ChangesViewUtil.createTreeModel(
        project,
        changesView.grouping,
        modelData.changeLists,
        modelData.unversionedFiles,
        modelData.ignoredFiles,
        modelData.isAllowExcludeFromCommit,
      )
    }

    checkCanceled()
    withContext(Dispatchers.EDT) {
      TRACER.spanBuilder(ChangesView.ChangesViewRefreshEdt.getName()).use {
        changesView.updateTreeModel(model, ChangesViewTreeStateStrategy())
        checkCanceled()
        synchronizeInclusion(modelData.changeLists, modelData.unversionedFiles)
      }
    }
  }

  abstract fun getModelData(): ModelData

  abstract fun synchronizeInclusion(changeLists: List<LocalChangeList>, unversionedFiles: List<FilePath>)

  /**
   * Immediately reset changes view and request refresh when NON_MODAL modality allows (i.e. after a plugin was unloaded or a dialog closed)
   */
  @RequiresEdt
  fun resetViewImmediatelyAndRefreshLater() {
    changesView.setModel(TreeModelBuilder.buildEmpty())
    changesView.setPaintBusy(true)
    scheduleRefreshNow()
  }

  private companion object {
    private val TRACER
      get() = TelemetryManager.getInstance().getTracer(VcsScope)
  }

  class ModelData(
    val changeLists: List<LocalChangeList>,
    val unversionedFiles: List<FilePath>,
    val ignoredFiles: List<FilePath>,
    val isAllowExcludeFromCommit: () -> Boolean,
  )
}

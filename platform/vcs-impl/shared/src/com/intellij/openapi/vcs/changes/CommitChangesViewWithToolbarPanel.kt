// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsConfiguration.getInstance
import com.intellij.openapi.vcs.changes.ChangesViewModifier.ChangesViewModifierListener
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.SingleTaskRunner
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewSettings
import com.intellij.platform.vcs.impl.shared.changes.PartialChangesHolder
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewApi
import com.intellij.platform.vcs.impl.shared.telemetry.ChangesView
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import com.intellij.util.application
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import fleet.rpc.client.durable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import java.awt.event.MouseEvent
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
    changesView.putClientProperty(ExpandableItemsHandler.IGNORE_ITEM_SELECTION, true)
  }

  private val inputHandler = ChangesViewInputHandler(cs, changesView)
  val diffRequests: SharedFlow<ChangesViewDiffAction> = inputHandler.diffRequests

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

private class ChangesViewInputHandler(private val cs: CoroutineScope, private val changesView: ChangesListView) {
  val diffRequests: MutableSharedFlow<ChangesViewDiffAction> =
    MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  val commitTwEnabled = flow {
    durable {
      emitAll(ChangesViewApi.getInstance().isCommitToolWindowEnabled(changesView.project.projectId()))
    }
  }.stateIn(cs, SharingStarted.Eagerly, true)

  init {
    changesView.doubleClickHandler = Processor { e: MouseEvent ->
      if (EditSourceOnDoubleClickHandler.isToggleEvent(changesView, e)) return@Processor false
      handleEnterOrDoubleClick(requestFocus = true)
    }
    changesView.enterKeyHandler = Processor {
      handleEnterOrDoubleClick(requestFocus = false)
    }
    changesView.addSelectionListener {
      if (Registry.get("show.diff.preview.as.editor.tab.with.single.click").asBoolean() &&
          getInstance(changesView.project).LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN) {
        diffRequests.tryEmit(ChangesViewDiffAction.TRY_SHOW_PREVIEW)
      }
    }
  }

  private fun handleEnterOrDoubleClick(requestFocus: Boolean): Boolean {
    if (!performHoverAction()) {
      if (isDiffPreviewOnDoubleClickOrEnter()) diffRequests.tryEmit(ChangesViewDiffAction.PERFORM_DIFF)
      else {
        val dataContext = DataManager.getInstance().getDataContext(changesView)
        cs.launch {
          val parameters = NavigationOptions.defaultOptions().requestFocus(requestFocus)
          NavigationService.getInstance(changesView.project).navigate(dataContext, parameters)
        }
      }
    }
    return true
  }

  private fun isDiffPreviewOnDoubleClickOrEnter(): Boolean =
    if (commitTwEnabled.value) VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK
    else VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK

  private fun performHoverAction(): Boolean {
    val selected = VcsTreeModelData.selected(changesView).iterateNodes().single()
    if (selected == null) return false

    for (extension in ChangesViewNodeAction.EP_NAME.getExtensions(changesView.project)) {
      if (extension.handleDoubleClick(selected)) return true
    }
    return false
  }
}

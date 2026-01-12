// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.ChangesViewModifier.ChangesViewModifierListener
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.vcs.impl.shared.SingleTaskRunner
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewDataKeys
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewSettings
import com.intellij.platform.vcs.impl.shared.changes.PartialChangesHolder
import com.intellij.platform.vcs.impl.shared.commit.CommitToolWindowViewModel
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitPresentation
import com.intellij.platform.vcs.impl.shared.telemetry.ChangesView
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.ui.ClickListener
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import com.intellij.util.application
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import java.awt.event.MouseEvent
import kotlin.time.Duration.Companion.milliseconds

private val REFRESH_DELAY = 100.milliseconds
private typealias RefreshCallback = suspend () -> Unit

@ApiStatus.Internal
abstract class CommitChangesViewWithToolbarPanel(
  changesView: ChangesListView,
  protected val cs: CoroutineScope,
) : ChangesViewPanel(changesView), UiDataProvider {
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
  val diffRequests: SharedFlow<Pair<ChangesViewDiffAction, ClientId>> = inputHandler.diffRequests

  @RequiresEdt
  open fun initPanel() {
    inputHandler.installListeners()

    cs.launch(Dispatchers.UI) {
      merge(
        ChangeListsViewModel.getInstance(project).changeListManagerState,
        PartialChangesHolder.getInstance(project).updates,
      ).collect {
        changesView.repaint()
      }
    }

    cs.launch(Dispatchers.UI) {
      project.serviceAsync<CommitToolWindowViewModel>().canExcludeFromCommit.collectLatest { canExclude ->
        changesView.isShowCheckboxes = canExclude
      }
    }

    cs.launch {
      project.serviceAsync<CommitToolWindowViewModel>().editedCommit.collect { editedCommit ->
        scheduleRefresh(withDelay = true, callback = editedCommit?.let(::getEditedCommitSelectCallback))
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
  fun scheduleRefreshNow(callback: RefreshCallback? = null) {
    scheduleRefresh(withDelay = false, callback = callback)
  }

  @RequiresEdt
  fun setGrouping(groupingKey: String) {
    changesView.groupingSupport.setGroupingKeysOrSkip(setOf(groupingKey))
    scheduleRefreshNow()
  }

  @CalledInAny
  protected fun scheduleRefresh(withDelay: Boolean, callback: RefreshCallback? = null) {
    if (!withDelay && callback == null) {
      refresher.request()
      return
    }

    cs.launch {
      if (withDelay) delay(REFRESH_DELAY)
      refresher.request()
      refresher.awaitNotBusy()
      callback?.invoke()
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

  private fun getEditedCommitSelectCallback(editedCommit: EditedCommitPresentation): RefreshCallback = {
    withContext(Dispatchers.UI) {
      changesView.findNodeInTree(editedCommit)?.let { node -> changesView.expandSafe(node) }
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

  override fun uiDataSnapshot(sink: DataSink) {
    sink[ChangesViewDataKeys.SETTINGS] = ChangesViewSettings.getInstance(project)
    sink[ChangesViewDataKeys.REFRESHER] = Runnable { scheduleRefreshNow() }
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

private class ChangesViewInputHandler(
  private val cs: CoroutineScope,
  private val changesView: ChangesListView,
) {
  val diffRequests: MutableSharedFlow<Pair<ChangesViewDiffAction, ClientId>> =
    MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  @RequiresEdt
  fun installListeners() {
    changesView.doubleClickHandler = Processor { e: MouseEvent ->
      if (EditSourceOnDoubleClickHandler.isToggleEvent(changesView, e)) return@Processor false
      handleEnterOrDoubleClick(requestFocus = true)
    }
    changesView.enterKeyHandler = Processor {
      handleEnterOrDoubleClick(requestFocus = false)
    }

    SingleClickDiffPreviewHandler(changesView) {
      diffRequests.tryEmit(ChangesViewDiffAction.SINGLE_CLICK_DIFF_PREVIEW to ClientId.current)
    }.install()
  }

  private fun handleEnterOrDoubleClick(requestFocus: Boolean): Boolean {
    if (!performHoverAction()) {
      val diffPreviewOnDoubleClickOrEnter = changesView.project.service<CommitToolWindowViewModel>().diffPreviewOnDoubleClickOrEnter
      if (diffPreviewOnDoubleClickOrEnter && changesView.selectedDiffableNode != null) {
        diffRequests.tryEmit(ChangesViewDiffAction.PERFORM_DIFF to ClientId.current)
      }
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

  private fun performHoverAction(): Boolean {
    val selected = VcsTreeModelData.selected(changesView).iterateNodes().single()
    if (selected == null) return false

    for (extension in ChangesViewNodeAction.EP_NAME.getExtensions(changesView.project)) {
      if (extension.handleDoubleClick(selected)) return true
    }
    return false
  }

  private class SingleClickDiffPreviewHandler(
    private val changesView: ChangesListView,
    private val previewDiff: () -> Unit,
  ) : ClickListener() {
    fun install() {
      installOn(changesView)
    }

    override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
      val showDiff = clickCount == 1 &&
                     event.button == MouseEvent.BUTTON1 &&
                     Registry.get("show.diff.preview.as.editor.tab.with.single.click").asBoolean() &&
                     VcsConfiguration.getInstance(changesView.project).LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN &&
                     !EditSourceOnDoubleClickHandler.isToggleEvent(changesView, event)
      if (showDiff) {
        previewDiff()
      }
      return showDiff
    }
  }
}

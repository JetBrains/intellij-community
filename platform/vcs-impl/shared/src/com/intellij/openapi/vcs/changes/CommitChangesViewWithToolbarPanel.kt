// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.CommonActionsManager
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangesViewModifier.ChangesViewModifierListener
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
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
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.util.application
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EdtInvocationManager.invokeLaterIfNeeded
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import java.lang.Runnable
import javax.swing.tree.DefaultTreeModel
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
class CommitChangesViewWithToolbarPanel(
  changesView: ChangesListView,
  private val cs: CoroutineScope,
) : ChangesViewPanel(changesView) {
  val project: Project get() = changesView.project
  private val settings get() = ChangesViewSettings.getInstance(project)

  private val refresher = SingleTaskRunner(cs, 100.milliseconds) {
    refreshView()
  }

  private var modelProvider: ModelProvider? = null

  var id: ChangesViewId? = null
    set(value) {
      if (field == null) field = value
      else error("Id is already assigned")
    }

  init {
    refresher.start()
  }

  @CalledInAny
  fun setBusy(busy: Boolean) {
    invokeLaterIfNeeded { changesView.setPaintBusy(busy) }
  }

  @RequiresEdt
  fun initPanel(modelProvider: ModelProvider) {
    checkNotNull(id)

    this.modelProvider = modelProvider

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

    toolbarActionGroup.addAll(createChangesToolbarActions(changesView))

    Initializer.EP_NAME.forEachExtensionSafe { it.init(cs, this) }

    ChangesViewModifier.KEY.addChangeListener(project, { resetViewImmediatelyAndRefreshLater() }, cs.asDisposable())
    project.messageBus.connect(cs).subscribe(ChangesViewModifier.TOPIC, ChangesViewModifierListener { scheduleRefresh() })

    scheduleRefresh()
  }

  @CalledInAny
  fun scheduleRefresh(){
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
  private fun scheduleRefresh(withDelay: Boolean, @RequiresBackgroundThread callback: Runnable? = null) {
    setBusy(true)
    if (withDelay) {
      refresher.request()
    }
    else {
      refresher.requestNow()
    }
    cs.launch {
      refresher.awaitNotBusy()
      callback?.run()
      setBusy(false)
    }
  }

  @RequiresBackgroundThread
  private suspend fun refreshView() {
    if (!cs.isActive || !project.isInitialized || application.isUnitTestMode) return
    val modelProvider = modelProvider ?: return

    val model = TRACER.spanBuilder(ChangesView.ChangesViewRefreshBackground.name).use {
      modelProvider.getModel(changesView.grouping)
    }

    checkCanceled()
    withContext(Dispatchers.EDT) {
      TRACER.spanBuilder(ChangesView.ChangesViewRefreshEdt.getName()).use {
        changesView.updateTreeModel(model.treeModel, ChangesViewTreeStateStrategy())
        checkCanceled()
        modelProvider.synchronizeInclusion(model.changeLists, model.unversionedFiles)
      }
    }
  }


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

    private fun createChangesToolbarActions(clView: ChangesListView): List<AnAction> {
      val actions = mutableListOf<AnAction?>()
      actions.add(CustomActionsSchema.getInstance().getCorrectedAction(ActionPlaces.CHANGES_VIEW_TOOLBAR))

      if (!isNewUI()) {
        actions.add(Separator.getInstance())
      }

      actions.add(ActionManager.getInstance().getAction("ChangesView.ViewOptions"))
      actions.add(CommonActionsManager.getInstance().createExpandAllHeaderAction(clView.treeExpander, clView))
      actions.add(CommonActionsManager.getInstance().createCollapseAllAction(clView.treeExpander, clView))
      actions.add(Separator.getInstance())
      actions.add(ActionManager.getInstance().getAction("ChangesView.SingleClickPreview"))
      actions.add(ActionManager.getInstance().getAction("Vcs.GroupedDiffToolbarAction"))
      actions.add(Separator.getInstance())

      return actions.filterNotNull()
    }
  }

  interface ModelProvider {
    fun getModel(grouping: ChangesGroupingPolicyFactory): ExtendedTreeModel

    fun synchronizeInclusion(changeLists: List<LocalChangeList>, unversionedFiles: List<FilePath>)

    class ExtendedTreeModel(val changeLists: List<LocalChangeList>, val unversionedFiles: List<FilePath>, val treeModel: DefaultTreeModel)
  }

  interface Initializer {
    fun init(scope: CoroutineScope, panel: CommitChangesViewWithToolbarPanel)

    companion object {
      internal val EP_NAME = ExtensionPointName<Initializer>("com.intellij.vcs.commitChangesViewInitializer")
    }
  }
}

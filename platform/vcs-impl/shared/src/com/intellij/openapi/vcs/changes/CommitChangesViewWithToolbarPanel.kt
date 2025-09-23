// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.CommonActionsManager
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangesViewModifier.ChangesViewModifierListener
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewSettings
import com.intellij.platform.vcs.impl.shared.telemetry.ChangesView
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EdtInvocationManager.invokeLaterIfNeeded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise
import org.jetbrains.concurrency.createError
import org.jetbrains.concurrency.rejectedPromise
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.tree.DefaultTreeModel

@ApiStatus.Internal
class CommitChangesViewWithToolbarPanel(changesView: ChangesListView, parentDisposable: Disposable) : ChangesViewPanel(changesView), Disposable {
  val project: Project get() = changesView.project
  private val settings get() = ChangesViewSettings.getInstance(project)

  private val scope = project.service<ScopeProvider>().cs.childScope("CommitChangesListWithToolbarPanel")
  private val refresher = BackgroundRefresher<Runnable>(javaClass.simpleName + " refresh", this)

  private var modelProvider: ModelProvider? = null

  private var disposed = false

  init {
    Disposer.register(parentDisposable, this)
  }

  @CalledInAny
  fun setBusy(busy: Boolean) {
    invokeLaterIfNeeded { changesView.setPaintBusy(busy) }
  }

  @RequiresEdt
  fun initPanel(modelProvider: ModelProvider) {
    this.modelProvider = modelProvider

    scope.launch(Dispatchers.UI) {
      ChangeListsViewModel.getInstance(project).changeListManagerState.collectLatest {
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

    Initializer.EP_NAME.forEachExtensionSafe { it.init(scope, this) }

    scheduleRefresh()
  }

  @CalledInAny
  fun scheduleRefresh() {
    scheduleRefreshWithDelay(100)
  }

  @CalledInAny
  fun scheduleRefreshNow(): Promise<*> =
    scheduleRefreshWithDelay(0)

  @RequiresEdt
  fun setGrouping(groupingKey: String) {
    changesView.groupingSupport.setGroupingKeysOrSkip(setOf(groupingKey))
    scheduleRefreshNow()
  }

  @CalledInAny
  private fun scheduleRefreshWithDelay(delayMillis: Int): Promise<*> {
    setBusy(true)
    return refresher.requestRefresh(delayMillis) { refreshView() }
      .thenAsync { callback ->
        if (callback != null)
          scope.launch(Dispatchers.EDT) { callback.run() }.asPromise()
        else
          rejectedPromise(createError("ChangesViewManager is not available", false))
      }
      .onProcessed { setBusy(false) }
  }

  @RequiresBackgroundThread
  private fun refreshView(): Runnable? {
    if (disposed || !project.isInitialized || application.isUnitTestMode) return null

    val modelProvider = modelProvider ?: return null
    TRACER.spanBuilder(ChangesView.ChangesViewRefreshBackground.name).use {
      val model = modelProvider.getModel(changesView.grouping)

      val indicator = ProgressManager.getInstance().progressIndicator
      indicator.checkCanceled()

      val wasCalled = AtomicBoolean(false) // ensure multiple merged refresh requests are applied once
      return Runnable {
        if (wasCalled.compareAndSet(false, true)) {
          refreshViewOnEdt(modelProvider, model.treeModel, model.changeLists, model.unversionedFiles, indicator.isCanceled)
        }
      }
    }
  }

  @RequiresEdt
  private fun refreshViewOnEdt(
    modelProvider: ModelProvider,
    treeModel: DefaultTreeModel,
    changeLists: List<LocalChangeList>,
    unversionedFiles: List<FilePath>,
    hasPendingRefresh: Boolean,
  ) {
    if (disposed) return

    TRACER.spanBuilder(ChangesView.ChangesViewRefreshEdt.getName()).use {
      changesView.updateTreeModel(treeModel, ChangesViewTreeStateStrategy())

      if (!hasPendingRefresh) {
        modelProvider.synchronizeInclusion(changeLists, unversionedFiles)
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

  override fun dispose() {
    scope.cancel()
    disposed = true
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

  @Service(Service.Level.PROJECT)
  internal class ScopeProvider(val cs: CoroutineScope)
}
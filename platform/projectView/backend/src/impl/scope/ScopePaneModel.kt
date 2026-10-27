// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.impl.scope

import com.intellij.ide.scopeView.NamedScopeFilter
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.ide.scopeView.ScopeViewTreeModel
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.isQualifiedModuleNamesEnabled
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.projectView.backend.pane.BackendProjectViewPaneService
import com.intellij.platform.projectView.impl.ProjectViewPsiExtractor
import com.intellij.platform.projectView.impl.ProjectViewPsiSelectTarget
import com.intellij.platform.projectView.impl.ProjectViewSelectNodeVisitorProvider
import com.intellij.platform.projectView.impl.ProjectViewSelectTarget
import com.intellij.platform.projectView.impl.ProjectViewTreeNodeProvider
import com.intellij.platform.projectView.impl.ProjectViewUpdater
import com.intellij.platform.projectView.impl.TreeBasedProjectViewPaneModel
import com.intellij.platform.projectView.impl.TreeStructureProjectViewNode
import com.intellij.platform.projectView.impl.TreeStructureSelectNodeVisitorProvider
import com.intellij.platform.projectView.impl.navigateToTreeStructureNodes
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneLoadChildrenOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneNavigateOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneSelectionOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneStateBuilder
import com.intellij.platform.projectView.pane.SelectInRequest
import com.intellij.platform.projectView.pane.SuspendingBackendProjectViewPaneStateAccessor
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsAccessor
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsService
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * A Project View pane showing a single scope, the new implementation of one sub-pane of [ScopeViewPane].
 *
 * Built on top of the legacy [ScopeViewTreeModel] for now. That model isn't an
 * [com.intellij.ide.util.treeView.AbstractTreeStructure] (it's a structure of its own, with its own change tracking
 * and its own thread), which is why this pane doesn't extend [com.intellij.platform.projectView.impl.TreeStructureBasedProjectViewPaneModel] and has a
 * dedicated node provider and updater instead.
 *
 * All scope panes share one model, see [SharedScopeViewTreeModel]. A managed pane is live or frozen.
 * A live pane runs a session on the model. A frozen pane keeps its last tree and resumes it when it becomes live again.
 */
@ApiStatus.Internal
class ScopePaneModel internal constructor(
  project: Project,
  filter: NamedScopeFilter,
  private val sharedModel: SharedScopeViewTreeModel,
) : TreeBasedProjectViewPaneModel<TreeStructureProjectViewNode>(project) {
  /**
   * The pane ID never changes, but the filter behind it does: a scope keeps its name while its contents are
   * edited, and the holder hands out a new [NamedScopeFilter] every time that happens.
   * The live session sets each new filter on the shared model, see [runLiveSession].
   */
  private val currentFilter = MutableStateFlow(filter)

  private val sessionState = MutableStateFlow(SessionState.UNMANAGED)

  /**
   * The nodes whose children the frontend has requested, until the backend loads them.
   * A request that comes while this pane is frozen, or while its session stops, has no session to run in.
   * So the next live session loads these children again.
   */
  private val requestedChildren = ConcurrentHashMap<Long, ProjectViewPaneLoadChildrenOptions>()

  private val paneId = paneId(filter)

  override val psi: ProjectViewPsiExtractor<TreeStructureProjectViewNode> = ScopeTreeStructurePsiExtractor(project, sharedModel)

  override suspend fun id(): ProjectViewPaneId = paneId

  override suspend fun presentableName(): @NlsSafe String = currentFilter.value.scope.presentableName

  override suspend fun order(): Int = 4

  /**
   * Re-applies the scope, which is what [ScopeViewPane.updateSelectedScope] does for the legacy pane: the filter
   * instance is replaced whenever the scope's contents change, and the model has to be told to re-filter.
   */
  fun updateFilter(filter: NamedScopeFilter) {
    currentFilter.value = filter
  }

  override suspend fun manageState(builder: ProjectViewPaneStateBuilder) {
    sessionState.value = SessionState.FROZEN
    try {
      sharedModel.runWithManagedPane(this) { model ->
        runLiveSession(model, builder)
      }
    }
    finally {
      sessionState.value = SessionState.UNMANAGED
      requestedChildren.clear() // the service clears the builder, so these node IDs mean nothing now
    }
  }

  /** The model shows the scope of the live pane. So only the live session sets the filter, here. */
  private suspend fun runLiveSession(model: ScopeViewTreeModel, builder: ProjectViewPaneStateBuilder) {
    try {
      coroutineScope {
        // Undispatched, so that the model has the filter of this pane before the session loads the first node.
        launch(CoroutineName("The filter of the scope pane $paneId"), start = CoroutineStart.UNDISPATCHED) {
          currentFilter.collect { filter -> model.setFilter(filter) }
        }
        LOG.debug { "The scope pane $paneId is live" }
        // If this pane was live before, the builder still has that tree, and this session resumes it.
        super.manageState(builder)
      }
    }
    finally {
      sessionState.value = SessionState.FROZEN
      LOG.debug { "The scope pane $paneId is frozen" }
    }
  }

  override suspend fun onStateChanged(state: SuspendingBackendProjectViewPaneStateAccessor<TreeStructureProjectViewNode>) {
    val resumed = sessionState.getAndUpdate { SessionState.LIVE } != SessionState.LIVE
    if (resumed) {
      sharedModel.setHasTree(this)
    }
    for ((parentId, options) in requestedChildren) {
      val parent = state.getNodeById(parentId)
      if (parent == null || state.getChildren(parent) != null) {
        requestedChildren.remove(parentId)
      }
      else if (resumed) {
        super.loadChildren(parentId, options)
      }
    }
  }

  override suspend fun setPaneSelected(isSelected: Boolean, options: ProjectViewPaneSelectionOptions) {
    if (!isSelected) return
    sharedModel.select(this)
    awaitLive()
  }

  override suspend fun loadChildren(parentId: Long, options: ProjectViewPaneLoadChildrenOptions) {
    requestedChildren[parentId] = options
    super.loadChildren(parentId, options)
  }

  /**
   * Searches the first pane whose scope contains the file, the same as the legacy `SelectInProjectViewImpl.doSelectInScopeViewPane`.
   * This pane goes first, then the other panes in the order of their scopes.
   */
  override suspend fun findNodeForSelectIn(selectInRequest: SelectInRequest): ProjectViewNodePath? {
    val target = computeSelectTarget(selectInRequest) ?: return null
    // The scope panes don't override computeSelectTarget, so the target is the default one.
    val file = (target as? ProjectViewPsiSelectTarget)?.file
    val pane = findPaneThatAccepts(file) ?: return null
    if (pane === this) return findNodeInThisPane(target)
    LOG.debug { "The scope of $paneId doesn't contain $file, searching ${pane.paneId} instead" }
    return BackendProjectViewPaneService.getInstance(project).withPaneActive(pane.paneId) {
      pane.findNodeInThisPane(target)
    }
  }

  private suspend fun findPaneThatAccepts(file: VirtualFile?): ScopePaneModel? {
    if (file == null) return this
    val panes = listOf(this) + ScopePaneService.getInstance(project).getPanes().filter { it !== this }
    return panes.firstOrNull { pane -> readAction { pane.currentFilter.value.accept(file) } }
  }

  /**
   * The search doesn't select this pane.
   * When the frontend selects this pane to show the found node, the pane becomes live again.
   */
  private suspend fun findNodeInThisPane(target: ProjectViewSelectTarget): ProjectViewNodePath? {
    return sharedModel.runFindRequest(this) {
      // The session of this pane starts in the same live period. A pane that isn't managed has no session.
      sessionState.first { it != SessionState.FROZEN }
      findNodeForSelectTarget(target)
    }
  }

  /**
   * Suspends until this pane is live.
   * Returns earlier if this pane isn't managed, because its next session can make it live anyway.
   * Also returns earlier if the rules of the shared model choose another pane since.
   */
  private suspend fun awaitLive() {
    combine(sessionState, sharedModel.livePane) { state, livePane ->
      state != SessionState.FROZEN || livePane !== this
    }.first { it }
  }

  override suspend fun createNodeProvider(settingsAccessor: ProjectViewPaneSettingsAccessor): ProjectViewTreeNodeProvider<TreeStructureProjectViewNode> {
    return sharedModel.createNodeProvider(settingsAccessor)
  }

  override suspend fun createUpdater(): ProjectViewUpdater = sharedModel.createUpdater()

  override fun createSelectNodeVisitorProvider(): ProjectViewSelectNodeVisitorProvider<TreeStructureProjectViewNode> {
    return TreeStructureSelectNodeVisitorProvider()
  }

  override suspend fun navigate(nodeIds: List<Long>, options: ProjectViewPaneNavigateOptions): Boolean {
    return navigateToTreeStructureNodes(project, nodeIds.mapNotNull { suspendingState?.getNodeById(it) }, options)
  }

  override fun supportsOption(option: ProjectViewPaneOption): Boolean = isOptionSupportedByScopePane(project, option)

  override fun supportsFileNesting(): Boolean = true

  override suspend fun setOptionValue(option: ProjectViewPaneOption, newValue: Boolean) {
    super.setOptionValue(option, newValue)
    // The model caches its children and the state that comes from the view settings, so re-reading the tree isn't enough.
    sharedModel.invalidate()
  }

  // ScopeViewTreeModel batches VFS and PSI changes in its own queue, which nothing outside it can observe.
  override suspend fun flushExternalUpdates(): Unit = sharedModel.flushUpdates()

  companion object {
    fun paneId(filter: NamedScopeFilter): ProjectViewPaneId = projectViewPaneId("${ScopeViewPane.ID}:$filter")
  }
}

/** The options of [ScopePaneModel]. [SharedScopeViewTreeModel] needs them without a pane. */
internal fun isOptionSupportedByScopePane(project: Project, option: ProjectViewPaneOption): Boolean {
  val settings = ProjectViewPaneSettingsService.getInstance(project)
  return when (option) {
    is ProjectViewPaneOption.AbbreviatePackageNames -> false
    is ProjectViewPaneOption.FlattenModules ->
      PlatformUtils.isIntelliJ() &&
      isQualifiedModuleNamesEnabled(project) &&
      settings.isOptionSelected(ProjectViewPaneOption.ShowModules)
    is ProjectViewPaneOption.HideEmptyMiddlePackages -> settings.isOptionSelected(ProjectViewPaneOption.FlattenPackages)
    is ProjectViewPaneOption.ShowModules -> PlatformUtils.isIntelliJ()
    is ProjectViewPaneOption.CompactDirectories -> false
    is ProjectViewPaneOption.ShowExcludedFiles -> false
    is ProjectViewPaneOption.ShowLibraryContents -> false
    is ProjectViewPaneOption.ShowScratchesAndConsoles -> false
    else -> true
  }
}

private enum class SessionState {
  /** The service doesn't manage this pane, and the builder is empty. */
  UNMANAGED,

  /** Another pane is live, or this pane waits for its session to start. The builder keeps the last tree. */
  FROZEN,

  /** The session of this pane runs on the shared model. */
  LIVE,
}

private val LOG = logger<ScopePaneModel>()

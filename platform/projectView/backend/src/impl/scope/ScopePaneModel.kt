// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.backend.impl.scope

import com.intellij.ide.projectView.impl.CompoundProjectViewNodeDecorator
import com.intellij.ide.projectView.impl.CompoundTreeStructureProvider
import com.intellij.ide.scopeView.NamedScopeFilter
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.ide.scopeView.ScopeViewTreeModel
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.isQualifiedModuleNamesEnabled
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.projectView.impl.ProjectViewPaneViewSettings
import com.intellij.platform.projectView.impl.ProjectViewPsiExtractor
import com.intellij.platform.projectView.impl.ProjectViewSelectNodeVisitorProvider
import com.intellij.platform.projectView.impl.ProjectViewTreeNodeProvider
import com.intellij.platform.projectView.impl.ProjectViewUpdater
import com.intellij.platform.projectView.impl.TreeBasedProjectViewPaneModel
import com.intellij.platform.projectView.impl.TreeStructureProjectViewNode
import com.intellij.platform.projectView.impl.TreeStructureSelectNodeVisitorProvider
import com.intellij.platform.projectView.impl.navigateToTreeStructureNode
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneNavigateOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneStateBuilder
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsAccessor
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsService
import com.intellij.platform.projectView.settings.projectViewPaneOption
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.annotations.ApiStatus
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume

/**
 * A Project View pane showing a single scope, the new implementation of one sub-pane of [ScopeViewPane].
 *
 * Built on top of the legacy [ScopeViewTreeModel] for now. That model isn't an
 * [com.intellij.ide.util.treeView.AbstractTreeStructure] (it's a structure of its own, with its own change tracking
 * and its own thread), which is why this pane doesn't extend [com.intellij.platform.projectView.impl.TreeStructureBasedProjectViewPaneModel] and has a
 * dedicated node provider and updater instead.
 */
@ApiStatus.Internal
class ScopePaneModel(
  project: Project,
  filter: NamedScopeFilter,
) : TreeBasedProjectViewPaneModel<TreeStructureProjectViewNode>(project) {
  /**
   * The pane ID never changes, but the filter behind it does: a scope keeps its name while its contents are
   * edited, and the holder hands out a new [NamedScopeFilter] every time that happens.
   */
  private val currentFilter = AtomicReference(filter)
  private val treeModel = AtomicReference<ScopeViewTreeModel?>(null)

  private val paneId = paneId(filter)

  override val psi: ProjectViewPsiExtractor<TreeStructureProjectViewNode> = ScopeTreeStructurePsiExtractor(project, treeModel)

  override suspend fun id(): ProjectViewPaneId = paneId

  override suspend fun presentableName(): @NlsSafe String = currentFilter.load().scope.presentableName

  override suspend fun order(): Int = 4

  /**
   * Re-applies the scope, which is what [ScopeViewPane.updateSelectedScope] does for the legacy pane: the filter
   * instance is replaced whenever the scope's contents change, and the model has to be told to re-filter.
   */
  fun updateFilter(filter: NamedScopeFilter) {
    currentFilter.store(filter)
    treeModel.load()?.setFilter(filter)
  }

  override suspend fun manageState(builder: ProjectViewPaneStateBuilder) {
    // The model is created per management session, so that it (and its VFS/PSI listeners) only exists
    // while somebody is actually looking at this pane.
    val model = ScopeViewTreeModel(project, ProjectViewPaneViewSettings(builder.asSettingsAccessor())).apply {
      setStructureProvider(CompoundTreeStructureProvider.get(project))
      setNodeDecorator(CompoundProjectViewNodeDecorator.get(project))
      setFilter(currentFilter.load())
    }
    treeModel.store(model)
    LOG.debug { "Created the scope tree model for $paneId" }
    try {
      super.manageState(builder)
    }
    finally {
      treeModel.store(null)
      Disposer.dispose(model)
      LOG.debug { "Disposed the scope tree model for $paneId" }
    }
  }

  override suspend fun createNodeProvider(settingsAccessor: ProjectViewPaneSettingsAccessor): ProjectViewTreeNodeProvider<TreeStructureProjectViewNode> {
    return ScopeViewNodeProvider(project, requireTreeModel(), settingsAccessor)
  }

  override suspend fun createUpdater(): ProjectViewUpdater = ScopeViewProjectViewUpdater(requireTreeModel())

  override fun createSelectNodeVisitorProvider(): ProjectViewSelectNodeVisitorProvider<TreeStructureProjectViewNode> {
    return TreeStructureSelectNodeVisitorProvider()
  }

  private fun requireTreeModel(): ScopeViewTreeModel = requireNotNull(treeModel.load()) {
    "The scope tree model of $paneId is only available while the pane is being managed"
  }

  override suspend fun navigate(nodeId: Long, options: ProjectViewPaneNavigateOptions) {
    navigateToTreeStructureNode(project, suspendingState?.getNodeById(nodeId), options)
  }

  override fun supportsOption(option: ProjectViewPaneOption): Boolean {
    val settings = ProjectViewPaneSettingsService.getInstance(project)
    return when (option) {
      is ProjectViewPaneOption.AbbreviatePackageNames -> false
      is ProjectViewPaneOption.FlattenModules ->
        PlatformUtils.isIntelliJ() &&
        isQualifiedModuleNamesEnabled(project) &&
        settings.isOptionSelected(projectViewPaneOption<ProjectViewPaneOption.ShowModules>())
      is ProjectViewPaneOption.HideEmptyMiddlePackages -> settings.isOptionSelected(projectViewPaneOption<ProjectViewPaneOption.FlattenPackages>())
      is ProjectViewPaneOption.ShowModules -> PlatformUtils.isIntelliJ()
      is ProjectViewPaneOption.CompactDirectories -> false
      is ProjectViewPaneOption.ShowExcludedFiles -> false
      is ProjectViewPaneOption.ShowLibraryContents -> false
      is ProjectViewPaneOption.ShowScratchesAndConsoles -> false
      else -> true
    }
  }

  override fun supportsFileNesting(): Boolean = true

  override suspend fun setOptionValue(option: ProjectViewPaneOption, newValue: Boolean) {
    super.setOptionValue(option, newValue)
    // ScopeViewTreeModel caches its children and its view-setting-derived state (such as whether excluded files
    // are shown), so re-reading the tree isn't enough: the model itself has to be invalidated. That fires a
    // whole-tree structure change, which ScopeViewProjectViewUpdater turns into a deep refresh.
    treeModel.load()?.invalidate(null)
  }

  override suspend fun flushExternalUpdates() {
    // ScopeViewTreeModel batches VFS and PSI changes in its own queue, which nothing outside it can observe.
    // This is the equivalent of what ScopeViewPane.select() does before it starts looking for a node.
    val model = treeModel.load() ?: return
    suspendCancellableCoroutine { continuation ->
      model.updater.updateImmediately { continuation.resume(Unit) }
    }
  }

  companion object {
    fun paneId(filter: NamedScopeFilter): ProjectViewPaneId = projectViewPaneId("${ScopeViewPane.ID}:$filter")
  }
}

private val LOG = logger<ScopePaneModel>()

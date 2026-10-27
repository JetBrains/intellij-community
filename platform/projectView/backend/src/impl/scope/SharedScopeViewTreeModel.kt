// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.impl.scope

import com.intellij.ide.projectView.impl.CompoundProjectViewNodeDecorator
import com.intellij.ide.projectView.impl.CompoundTreeStructureProvider
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.ide.scopeView.ScopeViewTreeModel
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.platform.projectView.impl.ProjectViewPaneViewSettings
import com.intellij.platform.projectView.impl.ProjectViewTreeNodeProvider
import com.intellij.platform.projectView.impl.ProjectViewUpdater
import com.intellij.platform.projectView.impl.TreeStructureProjectViewNode
import com.intellij.platform.projectView.settings.ProjectViewPaneFileNestingValue
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsAccessor
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsService
import com.intellij.platform.projectView.settings.ProjectViewPaneSortKey
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The one [ScopeViewTreeModel] of all scope panes, the same as the one model of the legacy [ScopeViewPane].
 *
 * One pane is live at a time. Its session runs on the model, and the model shows the scope of that pane.
 * The other managed panes are frozen. Their state builders keep the last tree, and nothing updates it.
 * [LiveSelection.livePane] tells which pane is live.
 *
 * The model exists while a pane runs code in [runWithManagedPane] or [runFindRequest].
 * The operations that don't depend on a scope are here, and they do nothing while there is no model.
 * Only a live session sets the scope on the model.
 */
internal class SharedScopeViewTreeModel(private val project: Project, coroutineScope: CoroutineScope) {
  /** Guards [modelUserCount] and the creation and disposal of the model. */
  private val modelLock = Mutex()
  private var modelUserCount = 0

  /** The model, written only under [modelLock]. */
  private val currentModel = MutableStateFlow<ScopeViewTreeModel?>(null)

  private val selection = MutableStateFlow(LiveSelection())

  /**
   * The live session, in which the code of [withLivePane] can run, and the number of such code blocks that run now.
   * The next session starts only after all the blocks of the previous one have finished.
   */
  private val gate = MutableStateFlow(LiveGate(session = null, runningBlocks = 0))

  /** The pane that [LiveSelection.livePane] chooses now. */
  val livePane: Flow<ScopePaneModel?> = selection.map { it.livePane }.distinctUntilChanged()

  init {
    // The only place that decides which pane is live. It's also the only place that creates sessions.
    coroutineScope.launch(CoroutineName("The live scope pane")) {
      // Each choice gets its own index, so the index is a unique ID of the session.
      livePane.withIndex().collect { (index, pane) ->
        LOG.trace { "Terminating the previous session ${gate.value.session}" }
        gate.update { it.copy(session = null) } // the blocks of the previous session stop, and no new block starts
        gate.first { it.runningBlocks == 0 }
        LOG.trace { "The previous session has been terminated" }
        val session = if (pane == null) null else LiveSession(id = index, pane = pane)
        gate.update { it.copy(session = session) }
        LOG.debug { "The live scope session is $session" }
      }
    }
  }

  /** Makes [pane] the selected pane, which the frontend shows. */
  fun select(pane: ScopePaneModel) {
    selection.update { it.copy(selectedPane = pane) }
  }

  /**
   * Runs [session] in each live period of [pane], until it is cancelled.
   *
   * The pane service calls it while it manages [pane], that is, while the state of [pane] is requested.
   * While [pane] is frozen, nothing runs.
   */
  suspend fun runWithManagedPane(pane: ScopePaneModel, session: suspend (ScopeViewTreeModel) -> Unit) {
    selection.update { it.copy(managedPanes = it.managedPanes + ManagedPane(pane, hasTree = false)) }
    try {
      withLivePane(pane, session)
    }
    finally {
      selection.update { it.copy(managedPanes = it.managedPanes.filter { managed -> managed.pane !== pane }) }
    }
  }

  /** Tells that the managed [pane] has its tree now. So it doesn't need a live period for its first tree anymore. */
  fun setHasTree(pane: ScopePaneModel) {
    selection.update { current ->
      current.copy(managedPanes = current.managedPanes.map { if (it.pane === pane) it.copy(hasTree = true) else it })
    }
  }

  /**
   * Makes [pane] live and runs [find] in it, for a Select In.
   *
   * The last request wins, and the panes of the previous ones wait frozen.
   * If [pane] stops being live before [find] completes, [find] runs again when [pane] is live again.
   */
  suspend fun <T> runFindRequest(pane: ScopePaneModel, find: suspend () -> T): T {
    val request = FindRequest(pane)
    selection.update { it.copy(findRequests = it.findRequests + request) }
    try {
      return withLivePane(pane) { find() }
    }
    finally {
      selection.update { it.copy(findRequests = it.findRequests - request) }
    }
  }

  /**
   * Runs [block] while [pane] is live, and returns its result.
   *
   * If [pane] stops being live before [block] completes, [block] is cancelled, and it runs again when [pane] is live again.
   * So a block that never completes, such as a session, runs once in each live period.
   * Only [block] gets the model, so only the live pane can set its scope.
   */
  private suspend fun <T> withLivePane(pane: ScopePaneModel, block: suspend (ScopeViewTreeModel) -> T): T {
    val model = acquire()
    try {
      return channelFlow {
        gate.map { it.session }.distinctUntilChanged().collectLatest { session ->
          if (!tryEnterBlock(requestedPane = pane, session = session)) return@collectLatest
          try {
            send(block(model))
          }
          finally {
            leaveBlock()
          }
        }
      }.first()
    }
    finally {
      withContext(NonCancellable) {
        release()
      }
    }
  }

  private suspend fun acquire(): ScopeViewTreeModel = modelLock.withLock {
    ++modelUserCount
    currentModel.value ?: createModel().also { currentModel.value = it }
  }

  private suspend fun release() = modelLock.withLock {
    if (--modelUserCount == 0) {
      currentModel.value?.let { Disposer.dispose(it) }
      currentModel.value = null
      LOG.debug { "Disposed the shared scope tree model" }
    }
  }

  /**
   * Counts a new block of [requestedPane] in [session], the session that `collectLatest` delivered.
   * Fails if [session] isn't a session of [requestedPane], or if it has ended since.
   */
  private fun tryEnterBlock(requestedPane: ScopePaneModel, session: LiveSession?): Boolean {
    if (session?.pane !== requestedPane) return false // Fail-fast path: we want one pane, the current collectLatest call got another.
    while (true) {
      val current = gate.value
      // Each session has its own ID, so a session that has ended never comes back.
      // So `collectLatest` is about to get the newer value and cancel this block.
      if (current.session?.id != session.id) return false
      if (gate.compareAndSet(current, current.copy(runningBlocks = current.runningBlocks + 1))) return true
    }
  }

  private fun leaveBlock() {
    // The decrement has no session condition, because the session might have changed.
    // In fact, setting the session to null is how the collector stops the running blocks.
    gate.update { it.copy(runningBlocks = it.runningBlocks - 1) }
  }

  private fun createModel(): ScopeViewTreeModel {
    val model = ScopeViewTreeModel(project, ProjectViewPaneViewSettings(ScopePaneSettingsAccessor(project))).apply {
      setStructureProvider(CompoundTreeStructureProvider.get(project))
      setNodeDecorator(CompoundProjectViewNodeDecorator.get(project))
    }
    // The contents of a VCS-based scope, such as "All Changed Files", depend on the file statuses.
    // Setting the same scope again makes the model filter the files again.
    FileStatusManager.getInstance(project).addFileStatusListener(object : FileStatusListener {
      override fun fileStatusesChanged() {
        model.setFilter(model.filter)
      }
    }, model)
    LOG.debug { "Created the shared scope tree model" }
    return model
  }

  /** Creates the node provider of a live session. */
  fun createNodeProvider(settings: ProjectViewPaneSettingsAccessor): ProjectViewTreeNodeProvider<TreeStructureProjectViewNode> {
    return ScopeViewNodeProvider(project, requireModel(), settings)
  }

  /** Creates the updater of a live session. */
  fun createUpdater(): ProjectViewUpdater = ScopeViewProjectViewUpdater(requireModel())

  /** The content of a node of the model, for [ScopeTreeStructurePsiExtractor]. */
  fun getContent(userObject: Any?): Any? = currentModel.value?.getContent(userObject)

  /**
   * Makes the model forget its children and its state that comes from the view settings, such as whether excluded files are shown.
   * It fires a whole-tree structure change, and [ScopeViewProjectViewUpdater] turns it into a deep refresh.
   */
  fun invalidate() {
    currentModel.value?.invalidate(null)
  }

  /**
   * Suspends until the model reports the VFS and PSI changes that it has collected.
   * This is the equivalent of what [ScopeViewPane.select] does before it starts looking for a node.
   */
  suspend fun flushUpdates() {
    val model = currentModel.value ?: return
    suspendCancellableCoroutine { continuation ->
      model.updater.updateImmediately { continuation.resume(Unit) }
    }
  }

  private fun requireModel(): ScopeViewTreeModel = requireNotNull(currentModel.value) {
    "The shared scope tree model is only available while a scope pane is live"
  }
}

/**
 * What decides the live pane. A managed pane stays frozen until it becomes live, see [livePane].
 *
 * [managedPanes] and [findRequests] are in the order in which they came.
 */
private data class LiveSelection(
  val selectedPane: ScopePaneModel? = null,
  val managedPanes: List<ManagedPane> = emptyList(),
  val findRequests: List<FindRequest> = emptyList(),
) {
  /**
   * The rules, in the order of priority:
   * 1. The pane of the last find request. A Select In must search a live pane.
   * 2. The last managed pane without a tree. The pane service waits for its first state before it does anything with it.
   * 3. The selected pane, if it's managed. The frontend doesn't tell a pane that it's no longer selected.
   * 4. The last managed pane.
   */
  val livePane: ScopePaneModel?
    get() = findRequests.lastOrNull()?.pane
            ?: managedPanes.lastOrNull { !it.hasTree }?.pane
            ?: selectedPane?.takeIf { selected -> managedPanes.any { it.pane === selected } }
            ?: managedPanes.lastOrNull()?.pane
}

private data class ManagedPane(val pane: ScopePaneModel, val hasTree: Boolean)

/** Not a data class: two requests for the same pane are different requests. */
private class FindRequest(val pane: ScopePaneModel)

/** A live period of [pane]. [id] tells two live periods of the same pane apart. */
private data class LiveSession(val id: Int, val pane: ScopePaneModel)

private data class LiveGate(val session: LiveSession?, val runningBlocks: Int)

/**
 * The settings of a scope pane, read from the settings service.
 *
 * The model reads them in its constructor, before a session puts the settings into its state builder.
 * The settings of a state builder are also empty after its pane stops, and the model can outlive that pane.
 * The values are the same as [ScopePaneModel] puts into a state builder.
 */
private class ScopePaneSettingsAccessor(private val project: Project) : ProjectViewPaneSettingsAccessor {
  private val service: ProjectViewPaneSettingsService
    get() = ProjectViewPaneSettingsService.getInstance(project)

  override fun isOptionSelected(option: ProjectViewPaneOption): Boolean {
    return service.isOptionSelected(option) && service.isOptionEnabled(option) && isOptionSupportedByScopePane(project, option)
  }

  override fun getSortKey(): ProjectViewPaneSortKey = service.getSortKey()

  override fun getFileNesting(): ProjectViewPaneFileNestingValue = service.getFileNesting() // scope panes support file nesting
}

private val LOG = logger<SharedScopeViewTreeModel>()

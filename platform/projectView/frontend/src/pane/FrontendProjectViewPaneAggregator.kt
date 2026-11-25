// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.frontend.pane

import com.intellij.ide.SelectInContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.project.projectId
import com.intellij.platform.projectView.actions.EditorChoice
import com.intellij.platform.projectView.frontend.impl.TreeBasedFrontendProjectViewPane
import com.intellij.platform.projectView.pane.BackendProjectViewSelectInChecker
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneKind
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneService
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.pane.SelectInContextDTO
import com.intellij.platform.projectView.pane.SelectInRequestDTO
import com.intellij.platform.projectView.pane.serialize
import com.intellij.platform.projectView.rpc.ProjectViewRpc
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.TestOnly
import java.lang.ref.WeakReference
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration

@Service(Service.Level.PROJECT)
internal class FrontendProjectViewPaneAggregator(
  private val project: Project,
  coroutineScope: CoroutineScope,
) {
  companion object {
    fun getInstance(project: Project): FrontendProjectViewPaneAggregator = project.service()
  }

  private val backendServiceDeferred = coroutineScope.async(CoroutineName("Waiting for the Project View backend")) {
    BackendDelegatingProjectViewPaneService(project, ProjectViewRpc.awaitConnectionAndGetInstance())
  }

  // null means "not loaded yet", as opposed to "loaded, but there are no panes"
  private val uiDescriptors = MutableStateFlow<Map<ProjectViewPaneId, ProjectViewPaneDescriptorImpl>?>(null)
  private val frontendDescriptors = MutableStateFlow<Map<ProjectViewPaneId, ProjectViewPaneDescriptorImpl>?>(null)
  private val backendDescriptors = MutableStateFlow<Map<ProjectViewPaneId, ProjectViewPaneDescriptorImpl>?>(null)

  private val isBackendLoaded = AtomicBoolean(false)

  /**
   * The answer of the last [prefetchCanSelect].
   * One answer is enough, because a Select In popup and the selection that it starts use one context.
   */
  @Volatile
  private var lastPrefetchedAnswer: PrefetchedCanSelectAnswer? = null

  @Volatile
  private var isRemoteBackendSimulated = false

  init {
    coroutineScope.launch(CoroutineName("PV UI pane descriptors fetching")) {
      pureUiService().getPaneDescriptorsFlow().collect { descriptors ->
        LOG.info("Loaded the UI pane descriptors: ${descriptors.joinToString { it.id.idString }}")
        uiDescriptors.value = descriptors.associateBy { it.id }
      }
    }
    coroutineScope.launch(CoroutineName("PV frontend pane descriptors fetching")) {
      frontendService().getPaneDescriptorsFlow().collect { descriptors ->
        LOG.info("Loaded the frontend PV pane descriptors: ${descriptors.joinToString { it.id.idString }}")
        frontendDescriptors.value = descriptors.associateBy { it.id }
      }
    }
    coroutineScope.launch(CoroutineName("PV backend pane descriptors fetching")) {
      backendService().getPaneDescriptorsFlow().collect { descriptors ->
        LOG.info("Loaded the backend PV pane descriptors: ${descriptors.joinToString { it.id.idString }}")
        backendDescriptors.value = descriptors.associateBy { it.id }
        isBackendLoaded.store(true)
      }
    }
  }

  private fun frontendService(): ProjectViewPaneService = FrontendProjectViewPaneService.getInstance(project)

  private suspend fun backendService(): ProjectViewPaneService = backendServiceDeferred.await()

  private fun pureUiService(): PureUiProjectViewPaneService = PureUiProjectViewPaneService.getInstance(project)
  
  private suspend fun paneService(descriptor: ProjectViewPaneDescriptorImpl): ProjectViewPaneService {
    return when (descriptor.kind) {
      ProjectViewPaneKind.BACKEND -> backendService()
      ProjectViewPaneKind.LIGHT -> frontendService()
      ProjectViewPaneKind.UI_ONLY -> pureUiService()
    }
  }

  fun getPaneDescriptorsFlow(): Flow<List<ProjectViewPaneDescriptorImpl>> {
    return combine(uiDescriptors, frontendDescriptors, backendDescriptors) { ui, frontend, backend ->
      val pureUiPaneDescriptors = ui.orEmpty()
      val fullPaneDescriptors = when {
        // The backend wins on ID collisions: that's how a light frontend pane is replaced by the real one.
        backend != null -> (frontend.orEmpty() + backend).values
        // In the monolith mode, the backend is immediately available, no point showing light panes.
        // LIGHT_MONOLITH has no backend modules until it moves to MONOLITH, so it shows the light panes.
        frontend != null && IdeProductMode.isLight -> frontend.values
        else -> emptyList()
      }
      val result = mutableListOf<ProjectViewPaneDescriptorImpl>()
      result += pureUiPaneDescriptors.values
      // The choice between a pure UI pane and a full pane is arbitrary, it's an error either way.
      // If we end up in this situation, it's most likely the legacy pane compatibility layer picked something.
      // So our best guess is to pick the pure UI one instead.
      for (fullDescriptor in fullPaneDescriptors) {
        if (fullDescriptor.id in pureUiPaneDescriptors) {
          LOG.warn(
            "Duplicate pane ID ${fullDescriptor.id}: " +
            "full = $fullDescriptor, " +
            "pure UI = ${pureUiPaneDescriptors[fullDescriptor.id]}"
          )
          continue
        }
        result += fullDescriptor
      }
      result.sortedBy { it.order }
    }.distinctUntilChanged()
  }

  @RequiresEdt
  fun createPane(descriptor: ProjectViewPaneDescriptorImpl): FrontendProjectViewPane? {
    return when (descriptor.kind) {
      ProjectViewPaneKind.BACKEND -> TreeBasedFrontendProjectViewPane(project, descriptor)
      ProjectViewPaneKind.LIGHT -> TreeBasedFrontendProjectViewPane(project, descriptor)
      ProjectViewPaneKind.UI_ONLY -> pureUiService().createPane(descriptor)
    }
  }

  suspend fun getPaneStateFlow(paneDescriptor: ProjectViewPaneDescriptorImpl): Flow<ProjectViewPaneStateEvent>? {
    return paneService(paneDescriptor).getPaneStateFlow(paneDescriptor.id)
  }

  suspend fun getPaneRequestChannel(paneDescriptor: ProjectViewPaneDescriptorImpl): SendChannel<ProjectViewPaneRequest>? {
    return paneService(paneDescriptor).getPaneRequestChannel(paneDescriptor.id)
  }

  suspend fun findNodeForOpenedFile(paneDescriptor: ProjectViewPaneDescriptorImpl, editorChoice: EditorChoice, isInvokedManually: Boolean): ProjectViewNodePath? {
    return paneService(paneDescriptor).findNodeForOpenedFile(paneDescriptor.id, editorChoice, isInvokedManually)
  }

  suspend fun findNodeForSelectIn(selectInRequest: SelectInRequestDTO): ProjectViewNodePath? {
    return if (isBackendLoaded.load()) {
      backendService().findNodeForSelectIn(selectInRequest)
    }
    else {
      frontendService().findNodeForSelectIn(selectInRequest)
    }
  }

  /**
   * Tells if the pane [paneId] can select the [context].
   * It asks the same side as [findNodeForSelectIn].
   * Call it in a read action, the same as [com.intellij.ide.SelectInTarget.canSelect].
   *
   * The backend panes answer directly only in the monolith mode, where the backend runs in this process.
   * In remote development, this function uses the answer of [prefetchCanSelect] for the [context].
   * Without an answer, it returns `true`, because a read action must not wait for an RPC.
   */
  fun canSelect(paneId: ProjectViewPaneId, context: SelectInContext): Boolean {
    if (!isBackendLoaded.load()) {
      return FrontendProjectViewPaneService.getInstance(project).canSelect(paneId, context)
    }
    val localBackend = localBackend()
    if (localBackend != null) {
      return localBackend.canSelect(paneId, context)
    }
    val answer = lastPrefetchedAnswer?.takeIf { it.isFor(context) } ?: return true
    return paneId !in answer.panesThatCannotSelect
  }

  /**
   * Tells if [canSelect] needs [prefetchCanSelect] for the [context].
   * It needs it when the backend runs in another process and there is no answer for the [context] yet.
   */
  fun isCanSelectPrefetchNeeded(context: SelectInContext): Boolean {
    return isBackendLoaded.load() && localBackend() == null && lastPrefetchedAnswer?.isFor(context) != true
  }

  /**
   * Fetches from the backend the panes that cannot select the [context], for [canSelect].
   * If the answer does not come in [timeout], [canSelect] returns `true` for the backend panes.
   */
  suspend fun prefetchCanSelect(context: SelectInContext, timeout: Duration) {
    if (!isCanSelectPrefetchNeeded(context)) return
    val contextDTO = context.serialize()
    val panesThatCannotSelect = withTimeoutOrNull(timeout) {
      backendServiceDeferred.await().getPanesThatCannotSelect(contextDTO)
    }
    if (panesThatCannotSelect == null) {
      LOG.info("The backend did not tell in $timeout which panes cannot select $context")
      return
    }
    LOG.debug { "The panes that cannot select $context: $panesThatCannotSelect" }
    lastPrefetchedAnswer = PrefetchedCanSelectAnswer(context, panesThatCannotSelect.toSet())
  }

  private fun localBackend(): BackendProjectViewSelectInChecker? {
    if (isRemoteBackendSimulated) return null
    return BackendProjectViewSelectInChecker.getInstanceIfLocal(project)
  }

  /** Makes this aggregator use the backend as if it ran in another process, until [disposable] is disposed. */
  @TestOnly
  fun simulateRemoteBackend(disposable: Disposable) {
    isRemoteBackendSimulated = true
    Disposer.register(disposable) { isRemoteBackendSimulated = false }
  }
}

/**
 * The panes that cannot select the context.
 * It keeps the context through a weak reference, so it does not keep the editor or the PSI of the context.
 */
private class PrefetchedCanSelectAnswer(context: SelectInContext, val panesThatCannotSelect: Set<ProjectViewPaneId>) {
  private val contextReference = WeakReference(context)

  fun isFor(context: SelectInContext): Boolean = contextReference.get() === context
}

private class BackendDelegatingProjectViewPaneService(
  private val project: Project,
  private val rpc: ProjectViewRpc,
) : ProjectViewPaneService {

  override suspend fun getPaneDescriptorsFlow(): Flow<Collection<ProjectViewPaneDescriptorImpl>> {
    return rpc.getPaneDescriptorsFlow(project.projectId()).map { dtos ->
      dtos.map { it.toDescriptor() }
    }
  }

  override suspend fun getPaneStateFlow(paneId: ProjectViewPaneId): Flow<ProjectViewPaneStateEvent>? {
    return rpc.getPaneStateFlow(project.projectId(), paneId)?.map { it.toEvent() }
  }

  override suspend fun getPaneRequestChannel(paneId: ProjectViewPaneId): SendChannel<ProjectViewPaneRequest>? {
    return rpc.getPaneRequestChannel(project.projectId(), paneId)
  }

  override suspend fun findNodeForOpenedFile(
    paneId: ProjectViewPaneId,
    editorChoice: EditorChoice,
    isInvokedManually: Boolean,
  ): ProjectViewNodePath? {
    return rpc.findNodeForOpenedFile(project.projectId(), paneId, editorChoice, isInvokedManually)
  }

  override suspend fun findNodeForSelectIn(selectInRequestDTO: SelectInRequestDTO): ProjectViewNodePath? {
    return rpc.findNodeForSelectIn(project.projectId(), selectInRequestDTO)
  }

  suspend fun getPanesThatCannotSelect(contextDTO: SelectInContextDTO): List<ProjectViewPaneId> {
    return rpc.getPanesThatCannotSelect(project.projectId(), contextDTO)
  }
}

private val LOG = logger<FrontendProjectViewPaneAggregator>()

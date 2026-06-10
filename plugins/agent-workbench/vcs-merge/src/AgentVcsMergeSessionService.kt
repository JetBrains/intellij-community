// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.vcs.merge

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchError
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.service.AgentDeferredNewSessionHandle
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.InvalidDiffRequestException
import com.intellij.diff.merge.MergeRequest
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.ReadOnlyModificationException
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.merge.MergeConflictIterativeDataHolder
import com.intellij.openapi.vcs.merge.MergeData
import com.intellij.openapi.vcs.merge.MergeProvider
import com.intellij.openapi.vcs.merge.MergeProvider2
import com.intellij.openapi.vcs.merge.MergeSession
import com.intellij.openapi.vcs.merge.MergeSessionEx
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal data class AgentVcsMergeLaunchRequest(
  @JvmField val selectionHintFiles: List<VirtualFile>,
  val agentProvider: AgentSessionProvider,
  @JvmField val launchMode: AgentSessionLaunchMode,
)

private data class MergeProviderScope(
  @JvmField val mergeProvider: MergeProvider,
  @JvmField val mergeSession: MergeSession?,
  @JvmField val files: MutableSet<VirtualFile>,
  @JvmField val nonBinaryFiles: Set<VirtualFile>,
)

private data class DiscoveredMergeProviderScope(
  @JvmField val mergeProvider: MergeProvider,
  @JvmField val files: List<VirtualFile>,
  @JvmField val nonBinaryFiles: List<VirtualFile>,
)

private data class DiscoveredMergeConflicts(
  @JvmField val hadConflicts: Boolean,
  @JvmField val scopes: List<DiscoveredMergeProviderScope>,
)

private data class ActiveAgentVcsMergeSession(
  @JvmField val providerScopes: MutableList<MergeProviderScope>,
  @JvmField val fileScopes: MutableMap<VirtualFile, MergeProviderScope>,
  @JvmField val iterativeDataHolder: MergeConflictIterativeDataHolder,
  val agentProvider: AgentSessionProvider,
  @JvmField val launchMode: AgentSessionLaunchMode,
  @JvmField val disposable: CheckedDisposable,
  @JvmField val unresolvedFiles: MutableList<VirtualFile>,
  @JvmField val selectionHintFiles: List<VirtualFile>,
  @Volatile @JvmField var threadFile: VirtualFile? = null,
)

internal sealed interface AgentVcsMergePreparationOutcome {
  data object NoSelectedConflicts : AgentVcsMergePreparationOutcome
  data object AutoResolved : AgentVcsMergePreparationOutcome
  data object Ready : AgentVcsMergePreparationOutcome
  data class Failed(@JvmField val message: @Nls String) : AgentVcsMergePreparationOutcome
}

internal suspend fun handleAgentVcsMergePreparationOutcome(
  outcome: AgentVcsMergePreparationOutcome,
  deferredHandle: AgentDeferredNewSessionHandle,
  initialMessageRequest: AgentPromptInitialMessageRequest,
  disposeSession: () -> Unit,
) {
  when (outcome) {
    AgentVcsMergePreparationOutcome.NoSelectedConflicts -> {
      try {
        deferredHandle.start(initialMessageRequest)
      }
      finally {
        disposeSession()
      }
    }

    AgentVcsMergePreparationOutcome.AutoResolved -> {
      deferredHandle.completeWithoutStart(
        title = AgentVcsMergeBundle.message("merge.agent.thread.completed.title"),
        message = AgentVcsMergeBundle.message("merge.agent.resolve.launch.success.auto.resolved"),
      )
      disposeSession()
    }

    AgentVcsMergePreparationOutcome.Ready -> {
      deferredHandle.start(initialMessageRequest)
    }

    is AgentVcsMergePreparationOutcome.Failed -> {
      deferredHandle.fail(
        title = AgentVcsMergeBundle.message("merge.agent.thread.failed.title"),
        message = outcome.message,
      )
      disposeSession()
    }
  }
}

@Service(Service.Level.PROJECT)
internal class AgentVcsMergeSessionService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) : com.intellij.openapi.Disposable {
  private val sessions = ConcurrentHashMap<String, ActiveAgentVcsMergeSession>()
  private val promptOnlyLaunchGate = AgentVcsMergeSessionSupport.PromptOnlyLaunchGate()

  fun startOrFocusSession(request: AgentVcsMergeLaunchRequest) {
    val selectionHintFiles = normalizeSelectionHintFiles(request.selectionHintFiles)
    val existing = sessions[PROJECT_WIDE_SESSION_KEY]
    if (existing != null) {
      if (!existing.disposable.isDisposed) {
        focusSession(existing)
        return
      }
      sessions.remove(PROJECT_WIDE_SESSION_KEY, existing)
    }

    val normalizedRequest = request.copy(selectionHintFiles = selectionHintFiles)
    if (selectionHintFiles.isEmpty()) {
      startPromptOnlySession(normalizedRequest)
      return
    }

    val session = createSession(normalizedRequest)
    val activeSession = sessions.putIfAbsent(PROJECT_WIDE_SESSION_KEY, session)
    if (activeSession != null) {
      Disposer.dispose(session.disposable)
      if (!activeSession.disposable.isDisposed) {
        focusSession(activeSession)
      }
      return
    }

    coroutineScope.launch(Dispatchers.Default) {
      val projectPath = project.basePath
      if (projectPath.isNullOrBlank()) {
        disposeSession(session)
        showError(AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.project.path"))
        return@launch
      }

      val deferredHandle = openDeferredHandle(
        projectPath = projectPath,
        request = normalizedRequest,
        openedChatHandler = { _, file ->
          session.threadFile = file
          pinThread(file)
          serviceAsync<AgentSessionUiPreferencesStateService>()
            .updateVcsMergeProviderPreferencesOnLaunch(session.agentProvider, session.launchMode)
        },
      )
      if (deferredHandle == null) {
        disposeSession(session)
        return@launch
      }

      handleAgentVcsMergePreparationOutcome(
        outcome = prepareSession(session),
        deferredHandle = deferredHandle,
        initialMessageRequest = buildInitialMessageRequest(projectPath, session.selectionHintFiles),
        disposeSession = { disposeSession(session) },
      )
    }
  }

  private fun startPromptOnlySession(request: AgentVcsMergeLaunchRequest) {
    if (!promptOnlyLaunchGate.tryStart()) return

    coroutineScope.launch(Dispatchers.Default) {
      try {
        val projectPath = project.basePath
        if (projectPath.isNullOrBlank()) {
          showError(AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.project.path"))
          return@launch
        }

        val deferredHandle = openDeferredHandle(
          projectPath = projectPath,
          request = request,
          openedChatHandler = { _, _ ->
            serviceAsync<AgentSessionUiPreferencesStateService>()
              .updateVcsMergeProviderPreferencesOnLaunch(request.agentProvider, request.launchMode)
          },
        ) ?: return@launch
        deferredHandle.start(buildInitialMessageRequest(projectPath, request.selectionHintFiles))
      }
      finally {
        promptOnlyLaunchGate.finish()
      }
    }
  }

  private suspend fun openDeferredHandle(
    projectPath: String,
    request: AgentVcsMergeLaunchRequest,
    openedChatHandler: suspend (Project, VirtualFile) -> Unit,
  ): AgentDeferredNewSessionHandle? {
    val deferredLaunch = try {
      serviceAsync<AgentSessionLaunchService>().createDeferredNewSession(
        path = projectPath,
        provider = request.agentProvider,
        mode = request.launchMode,
        entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
        preferredDedicatedFrame = false,
        openedChatHandler = openedChatHandler,
        updateGeneralProviderPreferences = false,
        threadTitle = AgentVcsMergeBundle.message("merge.agent.thread.title"),
        waitingTitle = AgentVcsMergeBundle.message("merge.agent.thread.preparing.title"),
        waitingMessage = AgentVcsMergeBundle.message("merge.agent.thread.preparing.body"),
      )
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (t: Throwable) {
      LOG.warn("Failed to open deferred merge agent thread", t)
      showError(AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.generic"))
      return null
    }

    val deferredHandle = deferredLaunch.handle
    if (deferredHandle == null) {
      showError(AgentPromptLaunchResult.failure(deferredLaunch.error ?: AgentPromptLaunchError.INTERNAL_ERROR).asMessage())
      return null
    }
    return deferredHandle
  }

  private fun createSession(request: AgentVcsMergeLaunchRequest): ActiveAgentVcsMergeSession {
    val disposable = Disposer.newCheckedDisposable(this, "AgentVcsMergeSession:$PROJECT_WIDE_SESSION_KEY")
    val session = ActiveAgentVcsMergeSession(
      providerScopes = Collections.synchronizedList(ArrayList()),
      fileScopes = ConcurrentHashMap(),
      iterativeDataHolder = MergeConflictIterativeDataHolder(project, disposable),
      agentProvider = request.agentProvider,
      launchMode = request.launchMode,
      disposable = disposable,
      unresolvedFiles = Collections.synchronizedList(ArrayList()),
      selectionHintFiles = request.selectionHintFiles,
    )
    registerExternalResolutionListener(session)
    Disposer.register(disposable) {
      releasePinnedThread(session)
      sessions.remove(PROJECT_WIDE_SESSION_KEY, session)
    }
    return session
  }

  private suspend fun prepareSession(session: ActiveAgentVcsMergeSession): AgentVcsMergePreparationOutcome {
    return try {
      val discoveredConflicts = discoverSelectedConflicts(project, session.selectionHintFiles)
      if (!discoveredConflicts.hadConflicts) {
        return AgentVcsMergePreparationOutcome.NoSelectedConflicts
      }
      if (discoveredConflicts.scopes.isEmpty()) {
        return AgentVcsMergePreparationOutcome.Failed(AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.unsupported"))
      }
      if (discoveredConflicts.scopes.none { scope -> scope.nonBinaryFiles.isNotEmpty() }) {
        return AgentVcsMergePreparationOutcome.Failed(AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.unsupported"))
      }

      val requestFactory = DiffRequestFactory.getInstance()
      for ((mergeProvider, files, nonBinaryFiles) in discoveredConflicts.scopes) {
        val mergeSession = (mergeProvider as? MergeProvider2)?.createMergeSession(files)
        val providerScope = MergeProviderScope(
          mergeProvider = mergeProvider,
          mergeSession = mergeSession,
          files = Collections.synchronizedSet(LinkedHashSet(files)),
          nonBinaryFiles = nonBinaryFiles.toSet(),
        )
        session.providerScopes.add(providerScope)

        for (file in files) {
          session.fileScopes[file] = providerScope
          session.addUnresolvedFile(file)
        }

        for (file in nonBinaryFiles) {
          val mergeData = loadMergeData(file, providerScope.mergeProvider)
          val request = createTextMergeRequest(project, file, requestFactory, mergeData)
          session.iterativeDataHolder.prepareModelIfSupported(file, request)
          withContext(Dispatchers.EDT) {
            session.iterativeDataHolder.resolveAutoResolvableConflicts(file)
          }
        }
      }

      val autoResolvedFiles = withContext(Dispatchers.EDT) {
        session.snapshotUnresolvedFiles().filter(session.iterativeDataHolder::isFileResolved)
      }
      autoResolvedFiles.forEach { file ->
        finalizeResolvedFile(session, file, disposeWhenEmpty = false)
      }

      val unresolvedFiles = session.snapshotUnresolvedFiles()
      if (unresolvedFiles.isEmpty()) {
        AgentVcsMergePreparationOutcome.AutoResolved
      }
      else {
        val launchableFiles = unresolvedFiles.filter { file ->
          session.fileScopes[file]?.nonBinaryFiles?.contains(file) == true
        }
        if (launchableFiles.isEmpty()) AgentVcsMergePreparationOutcome.Failed(AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.unsupported"))
        else AgentVcsMergePreparationOutcome.Ready
      }
    }
    catch (_: VcsException) {
      AgentVcsMergePreparationOutcome.Failed(AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.prepare"))
    }
    catch (e: InvalidDiffRequestException) {
      AgentVcsMergePreparationOutcome.Failed(e.asUserMessage())
    }
  }

  private suspend fun finalizeResolvedFile(
    session: ActiveAgentVcsMergeSession,
    file: VirtualFile,
    disposeWhenEmpty: Boolean = true,
  ) {
    if (!session.isUnresolved(file)) return

    withContext(Dispatchers.UiWithModelAccess) {
      saveDocument(file)
      checkMarkModifiedProject(project, file)
    }
    withContext(Dispatchers.Default) {
      markFilesProcessed(session, listOf(file))
    }
    if (disposeWhenEmpty && session.snapshotUnresolvedFiles().isEmpty()) {
      disposeSession(session)
    }
  }

  private fun buildInitialMessageRequest(
    projectPath: String,
    selectionHintFiles: List<VirtualFile>,
  ): AgentPromptInitialMessageRequest {
    return AgentPromptInitialMessageRequest(
      prompt = AgentVcsMergeSessionSupport.buildInitialPrompt(),
      projectPath = projectPath,
      contextItems = listOfNotNull(
        AgentVcsMergeSessionSupport.buildSelectionHintContextItem(
          selectionHintFiles.map { file -> toProjectRelativePath(file, project) },
        ),
      ),
    )
  }

  private fun focusSession(session: ActiveAgentVcsMergeSession) {
    val threadFile = session.threadFile ?: return
    coroutineScope.launch(Dispatchers.UiWithModelAccess) {
      serviceAsync<AgentSessionUiPreferencesStateService>()
        .updateVcsMergeProviderPreferencesOnLaunch(session.agentProvider, session.launchMode)
      FileEditorManagerEx.getInstanceExAsync(project)
        .openFile(threadFile, options = FileEditorOpenOptions(requestFocus = true, reuseOpen = true))
    }
  }

  private suspend fun pinThread(file: VirtualFile) {
    val editorManager = FileEditorManagerEx.getInstanceExAsync(project)
    withContext(Dispatchers.EDT) {
      editorManager.windows.firstOrNull { window -> window.isFileOpen(file) }?.setFilePinned(file, true)
    }
  }

  private fun releasePinnedThread(session: ActiveAgentVcsMergeSession) {
    val threadFile = session.threadFile ?: return
    coroutineScope.launch(Dispatchers.UiWithModelAccess) {
      val editorManager = FileEditorManagerEx.getInstanceExIfCreated(project) ?: return@launch
      editorManager.windows.firstOrNull { window -> window.isFileOpen(threadFile) }?.setFilePinned(threadFile, false)
    }
  }

  private fun registerExternalResolutionListener(session: ActiveAgentVcsMergeSession) {
    FileStatusManager.getInstance(project).addFileStatusListener(object : FileStatusListener {
      override fun fileStatusChanged(virtualFile: VirtualFile) {
        if (!session.isUnresolved(virtualFile)) return
        scheduleExternalResolutionCheck(session, listOf(virtualFile))
      }

      override fun fileStatusesChanged() {
        scheduleExternalResolutionCheck(session, session.snapshotUnresolvedFiles())
      }
    }, session.disposable)
  }

  private fun scheduleExternalResolutionCheck(session: ActiveAgentVcsMergeSession, candidateFiles: List<VirtualFile>) {
    val pendingFiles = candidateFiles
      .filter { file -> session.isUnresolved(file) }
      .distinct()
    if (pendingFiles.isEmpty()) return

    coroutineScope.launch(Dispatchers.Default) {
      if (session.disposable.isDisposed) return@launch

      val resolvedFiles = AgentVcsMergeSessionSupport.collectExternallyResolvedFiles(
        candidateFiles = pendingFiles,
        getStatus = { file -> FileStatusManager.getInstance(project).getStatus(file) },
      )

      resolvedFiles.forEach { file ->
        if (!session.disposable.isDisposed) {
          finalizeResolvedFile(session, file)
        }
      }
    }
  }

  private fun disposeSession(session: ActiveAgentVcsMergeSession) {
    if (!session.disposable.isDisposed) {
      Disposer.dispose(session.disposable)
    }
  }

  private fun markFilesProcessed(session: ActiveAgentVcsMergeSession, files: List<VirtualFile>) {
    val resolution = MergeSession.Resolution.Merged
    val filesByScope = LinkedHashMap<MergeProviderScope, MutableList<VirtualFile>>()
    for (file in files) {
      val scope = session.fileScopes[file] ?: continue
      filesByScope.getOrPut(scope) { ArrayList() }.add(file)
    }

    filesByScope.forEach { (scope, scopeFiles) ->
      val mergeSession = scope.mergeSession
      if (mergeSession is MergeSessionEx) {
        mergeSession.conflictResolvedForFiles(scopeFiles, resolution)
      }
      else if (mergeSession != null) {
        scopeFiles.forEach { file ->
          mergeSession.conflictResolvedForFile(file, resolution)
        }
      }
      else {
        scopeFiles.forEach { file ->
          scope.mergeProvider.conflictResolvedForFile(file)
        }
      }

      scopeFiles.forEach { file ->
        session.fileScopes.remove(file)
      }
      synchronized(scope.files) {
        scope.files.removeAll(scopeFiles.toSet())
      }
    }

    session.removeUnresolvedFiles(files)
    VcsDirtyScopeManager.getInstance(project).filesDirty(files, emptyList())
  }

  private fun showError(message: @Nls String) {
    coroutineScope.launch(Dispatchers.UiWithModelAccess) {
      Messages.showErrorDialog(project, message, AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.title"))
    }
  }

  private fun AgentPromptLaunchResult.asMessage(): @Nls String {
    return when (error) {
      AgentPromptLaunchError.PROVIDER_UNAVAILABLE -> AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.provider")
      AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE -> AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.mode")
      AgentPromptLaunchError.CANCELLED,
      AgentPromptLaunchError.DROPPED_DUPLICATE,
        -> AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.cancelled")

      else -> AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.generic")
    }
  }

  override fun dispose() {
    sessions.clear()
  }

  private companion object {
    private const val PROJECT_WIDE_SESSION_KEY = "project-wide"
  }
}

private fun discoverSelectedConflicts(
  project: Project,
  selectionHintFiles: List<VirtualFile>,
): DiscoveredMergeConflicts {
  val conflictFiles = AgentVcsMergeSessionSupport.collectSelectedConflictFiles(
    selectionHintFiles = selectionHintFiles,
    getStatus = { file -> FileStatusManager.getInstance(project).getStatus(file) },
  )
  val scopes = LinkedHashMap<MergeProvider, MutableList<VirtualFile>>()
  val vcsManager = ProjectLevelVcsManager.getInstance(project)

  for (file in conflictFiles) {
    val mergeProvider = vcsManager.getVcsFor(file)?.mergeProvider ?: continue
    scopes.getOrPut(mergeProvider) { ArrayList() }.add(file)
  }

  return DiscoveredMergeConflicts(
    hadConflicts = conflictFiles.isNotEmpty(),
    scopes = scopes.entries.map { (mergeProvider, files) ->
      DiscoveredMergeProviderScope(
        mergeProvider = mergeProvider,
        files = files,
        nonBinaryFiles = AgentVcsMergeSessionSupport.collectNonBinaryConflictFiles(files, mergeProvider),
      )
    },
  )
}

private fun ActiveAgentVcsMergeSession.snapshotUnresolvedFiles(): List<VirtualFile> {
  synchronized(unresolvedFiles) {
    return unresolvedFiles.toList()
  }
}

private fun ActiveAgentVcsMergeSession.addUnresolvedFile(file: VirtualFile) {
  synchronized(unresolvedFiles) {
    if (!unresolvedFiles.contains(file)) {
      unresolvedFiles.add(file)
    }
  }
}

private fun ActiveAgentVcsMergeSession.removeUnresolvedFiles(files: List<VirtualFile>) {
  synchronized(unresolvedFiles) {
    unresolvedFiles.removeAll(files.toSet())
  }
}

private fun ActiveAgentVcsMergeSession.isUnresolved(file: VirtualFile): Boolean {
  synchronized(unresolvedFiles) {
    return unresolvedFiles.contains(file)
  }
}

private fun createTextMergeRequest(
  project: Project,
  file: VirtualFile,
  requestFactory: DiffRequestFactory,
  mergeData: MergeData,
): MergeRequest {
  val byteContents = listOf(mergeData.CURRENT, mergeData.ORIGINAL, mergeData.LAST)

  return requestFactory.createTextMergeRequest(
    project,
    file,
    byteContents,
    mergeData.CONFLICT_TYPE,
    null,
    listOf(null, null, null),
    null,
  )
}

private suspend fun loadMergeData(
  file: VirtualFile,
  mergeProvider: MergeProvider,
): MergeData {
  return withContext(Dispatchers.IO) {
    mergeProvider.loadRevisions(file)
  }
}

private fun InvalidDiffRequestException.asUserMessage(): @Nls String {
  return when (cause) {
    is FileTooBigException -> VcsBundle.message("multiple.file.merge.dialog.message.file.too.big.to.be.loaded")
    is ReadOnlyModificationException -> DiffBundle.message("error.cant.resolve.conflicts.in.a.read.only.file")
    else -> {
      LOG.error(this)
      message ?: AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.prepare")
    }
  }
}

private fun checkMarkModifiedProject(project: Project?, file: VirtualFile) {
  com.intellij.diff.merge.MergeUtil.reportProjectFileChangeIfNeeded(project, file)
}

private fun saveDocument(file: VirtualFile) {
  val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return
  runWriteAction {
    FileDocumentManager.getInstance().saveDocument(document)
  }
}

@Internal
object AgentVcsMergeSessionSupport {
  class PromptOnlyLaunchGate {
    private val inProgress = AtomicBoolean(false)

    fun tryStart(): Boolean = inProgress.compareAndSet(false, true)

    fun finish() {
      inProgress.set(false)
    }
  }

  fun collectSelectedConflictFiles(
    selectionHintFiles: List<VirtualFile>,
    getStatus: (VirtualFile) -> FileStatus,
  ): List<VirtualFile> {
    return normalizeSelectionHintFiles(selectionHintFiles).filter { file ->
      isMergeConflictStatus(getStatus(file))
    }
  }

  fun collectNonBinaryConflictFiles(
    files: Collection<VirtualFile>,
    mergeProvider: MergeProvider,
  ): List<VirtualFile> {
    return files.filter { file -> !mergeProvider.isBinary(file) }
  }

  fun buildInitialPrompt(): String {
    return buildString {
      appendLine("Resolve the current merge conflicts for this IntelliJ IDEA worktree.")
      appendLine("Determine the active conflicted files yourself using normal IDE tools, VCS integrations, or git commands.")
      appendLine("Use normal IDE tools, git workflow, file edits, and any installed skills.")
      appendLine("Success means this worktree leaves VCS conflict state for the current merge-related operation.")
      appendLine(
        "If this worktree is in the middle of a Git merge, rebase, or cherry-pick, stage resolved files and continue that operation when needed.",
      )
      append("Ask follow-up questions in this thread if the intended merge result is unclear.")
    }
  }

  fun buildSelectionHintContextItem(selectionHintPaths: List<String>): AgentPromptContextItem? {
    if (selectionHintPaths.isEmpty()) return null

    val normalizedPaths = LinkedHashSet<String>()
    selectionHintPaths.forEach { path ->
      val normalizedPath = path.trim()
      if (normalizedPath.isNotEmpty()) {
        normalizedPaths.add(normalizedPath)
      }
    }
    if (normalizedPaths.isEmpty()) return null

    val includedPaths = normalizedPaths.take(MAX_SELECTION_HINT_PATHS)
    val fullBody = normalizedPaths.joinToString(separator = "\n") { path -> "file: $path" }
    val body = includedPaths.joinToString(separator = "\n") { path -> "file: $path" }
    val payloadEntries = includedPaths.map { path ->
      AgentPromptPayload.obj(
        "kind" to AgentPromptPayload.str("file"),
        "path" to AgentPromptPayload.str(path),
      )
    }
    val truncationReason = if (normalizedPaths.size > includedPaths.size) {
      AgentPromptContextTruncationReason.SOURCE_LIMIT
    }
    else {
      AgentPromptContextTruncationReason.NONE
    }

    return AgentPromptContextItem(
      rendererId = AgentPromptContextRendererIds.PATHS,
      title = "Launch Selection",
      body = body,
      payload = AgentPromptPayload.obj(
        "entries" to AgentPromptPayloadValue.Arr(payloadEntries),
        "selectedCount" to AgentPromptPayload.num(normalizedPaths.size),
        "includedCount" to AgentPromptPayload.num(includedPaths.size),
        "fileCount" to AgentPromptPayload.num(includedPaths.size),
      ),
      itemId = "vcsMerge.selection",
      source = "vcsMerge",
      truncation = AgentPromptContextTruncation(
        originalChars = fullBody.length,
        includedChars = body.length,
        reason = truncationReason,
      ),
    )
  }

  fun isMergeConflictStatus(status: FileStatus): Boolean {
    return status === FileStatus.MERGED_WITH_CONFLICTS || status === FileStatus.MERGED_WITH_BOTH_CONFLICTS
  }

  fun collectExternallyResolvedFiles(
    candidateFiles: List<VirtualFile>,
    getStatus: (VirtualFile) -> FileStatus,
  ): List<VirtualFile> {
    return candidateFiles.filter { file ->
      !isMergeConflictStatus(getStatus(file))
    }
  }
}

private val LOG = Logger.getInstance(AgentVcsMergeSessionService::class.java)

private const val MAX_SELECTION_HINT_PATHS = 20

private fun normalizeSelectionHintFiles(files: List<VirtualFile>): List<VirtualFile> {
  val uniquePaths = LinkedHashSet<String>()
  val result = ArrayList<VirtualFile>()
  for (file in files) {
    if (!file.isValid) continue
    if (uniquePaths.add(file.path)) {
      result.add(file)
    }
  }
  return result
}

internal fun toProjectRelativePath(file: VirtualFile, project: Project): String {
  val basePath = project.basePath ?: return file.path
  return FileUtilRt.getRelativePath(basePath, file.path, '/') ?: file.path
}

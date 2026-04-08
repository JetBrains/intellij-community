// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.vcs.merge

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchError
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.InvalidDiffRequestException
import com.intellij.diff.merge.MergeRequest
import com.intellij.diff.util.DiffUtil
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
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.merge.MergeConflictAiFileSnapshot
import com.intellij.openapi.vcs.merge.MergeConflictIterativeDataHolder
import com.intellij.openapi.vcs.merge.MergeData
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeProvider
import com.intellij.openapi.vcs.merge.MergeProvider2
import com.intellij.openapi.vcs.merge.MergeSession
import com.intellij.openapi.vcs.merge.MergeSessionEx
import com.intellij.openapi.vcs.merge.MergeUtils
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class AgentVcsMergeLaunchRequest(
  @JvmField val files: List<VirtualFile>,
  @JvmField val mergeProvider: MergeProvider,
  @JvmField val mergeDialogCustomizer: MergeDialogCustomizer,
  val agentProvider: AgentSessionProvider,
  @JvmField val launchMode: AgentSessionLaunchMode,
)

@Internal
data class AgentVcsMergePromptFileContext(
  @JvmField val projectRelativePath: String,
  @JvmField val binary: Boolean,
  @JvmField val totalConflicts: Int?,
  @JvmField val resolvedConflicts: Int?,
  @JvmField val unresolvedConflicts: Int?,
  @JvmField val yoursTitle: String?,
  @JvmField val baseTitle: String?,
  @JvmField val theirsTitle: String?,
  @JvmField val yoursRevision: String?,
  @JvmField val baseRevision: String?,
  @JvmField val theirsRevision: String?,
)

private data class ActiveAgentVcsMergeSession(
  @JvmField val sessionId: String,
  @JvmField val files: List<VirtualFile>,
  @JvmField val mergeProvider: MergeProvider,
  @JvmField val mergeDialogCustomizer: MergeDialogCustomizer,
  @JvmField val mergeSession: MergeSession?,
  @JvmField val iterativeDataHolder: MergeConflictIterativeDataHolder,
  val agentProvider: AgentSessionProvider,
  @JvmField val launchMode: AgentSessionLaunchMode,
  @JvmField val disposable: CheckedDisposable,
  @JvmField val unresolvedFiles: MutableList<VirtualFile>,
  @JvmField val promptFileContexts: MutableMap<VirtualFile, AgentVcsMergePromptFileContext>,
  @Volatile @JvmField var threadFile: VirtualFile? = null,
)

private sealed interface PreparationOutcome {
  data object AutoResolved : PreparationOutcome
  data object Ready : PreparationOutcome
  data class Failed(@JvmField val message: @Nls String) : PreparationOutcome
}

@Service(Service.Level.PROJECT)
internal class AgentVcsMergeSessionService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) : com.intellij.openapi.Disposable {
  private val sessions = ConcurrentHashMap<String, ActiveAgentVcsMergeSession>()

  fun startOrFocusSession(request: AgentVcsMergeLaunchRequest) {
    val sessionKey = buildSessionKey(request.files)
    val existing = sessions[sessionKey]
    if (existing != null) {
      if (!existing.disposable.isDisposed) {
        focusSession(existing)
        return
      }
      sessions.remove(sessionKey, existing)
    }

    val session = createSession(request, sessionKey)
    val activeSession = sessions.putIfAbsent(sessionKey, session)
    if (activeSession != null) {
      Disposer.dispose(session.disposable)
      if (!activeSession.disposable.isDisposed) {
        focusSession(activeSession)
      }
      return
    }

    coroutineScope.launch(Dispatchers.Default) {
      when (val outcome = prepareSession(session)) {
        PreparationOutcome.AutoResolved -> {
          disposeSession(session)
          notifySuccess(AgentVcsMergeBundle.message("merge.agent.resolve.launch.success.auto.resolved"))
        }

        PreparationOutcome.Ready -> launchAgentThread(session)

        is PreparationOutcome.Failed -> {
          disposeSession(session)
          showError(outcome.message)
        }
      }
    }
  }

  private fun createSession(
    request: AgentVcsMergeLaunchRequest,
    sessionKey: String,
  ): ActiveAgentVcsMergeSession {
    val disposable = Disposer.newCheckedDisposable(this, "AgentVcsMergeSession:$sessionKey")
    val session = ActiveAgentVcsMergeSession(
      sessionId = UUID.randomUUID().toString(),
      files = request.files,
      mergeProvider = request.mergeProvider,
      mergeDialogCustomizer = request.mergeDialogCustomizer,
      mergeSession = (request.mergeProvider as? MergeProvider2)?.createMergeSession(request.files),
      iterativeDataHolder = MergeConflictIterativeDataHolder(project, disposable),
      agentProvider = request.agentProvider,
      launchMode = request.launchMode,
      disposable = disposable,
      unresolvedFiles = Collections.synchronizedList(request.files.toMutableList()),
      promptFileContexts = ConcurrentHashMap(),
    )
    registerExternalResolutionListener(session)
    Disposer.register(disposable) {
      releasePinnedThread(session)
      sessions.remove(sessionKey, session)
    }
    return session
  }

  private suspend fun prepareSession(session: ActiveAgentVcsMergeSession): PreparationOutcome {
    return try {
      val requestFactory = DiffRequestFactory.getInstance()
      for (file in session.files) {
        val conflictData = loadConflictData(file, session.mergeProvider, session.mergeDialogCustomizer)
        val request = createMergeRequest(project, file, requestFactory, session.mergeProvider, conflictData)
        session.iterativeDataHolder.prepareModelIfSupported(file, request)
        val snapshot = withContext(Dispatchers.EDT) {
          session.iterativeDataHolder.resolveAutoResolvableConflicts(file)
          session.iterativeDataHolder.getAiFileSnapshot(file)
        }
        session.promptFileContexts[file] =
          buildPromptFileContext(project, file, conflictData, snapshot, session.mergeProvider.isBinary(file))
      }

      val autoResolvedFiles = withContext(Dispatchers.EDT) {
        session.files.filter(session.iterativeDataHolder::isFileResolved)
      }
      autoResolvedFiles.forEach { file ->
        finalizeResolvedFile(session, file)
      }

      val unresolvedFiles = session.snapshotUnresolvedFiles()
      if (unresolvedFiles.isEmpty()) {
        PreparationOutcome.AutoResolved
      }
      else {
        val launchableFiles = unresolvedFiles.filter { file ->
          session.promptFileContexts[file]?.binary != true
        }
        if (launchableFiles.isEmpty()) PreparationOutcome.Failed(AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.unsupported"))
        else PreparationOutcome.Ready
      }
    }
    catch (_: VcsException) {
      PreparationOutcome.Failed(AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.prepare"))
    }
    catch (e: InvalidDiffRequestException) {
      PreparationOutcome.Failed(e.asUserMessage())
    }
  }

  private suspend fun finalizeResolvedFile(session: ActiveAgentVcsMergeSession, file: VirtualFile) {
    if (!session.isUnresolved(file)) return

    withContext(Dispatchers.UiWithModelAccess) {
      saveDocument(file)
      checkMarkModifiedProject(project, file)
    }
    withContext(Dispatchers.Default) {
      markFilesProcessed(session, listOf(file))
    }
    if (session.snapshotUnresolvedFiles().isEmpty()) {
      disposeSession(session)
    }
  }

  private suspend fun launchAgentThread(session: ActiveAgentVcsMergeSession) {
    val projectPath = project.basePath
    if (projectPath.isNullOrBlank()) {
      disposeSession(session)
      showError(AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.project.path"))
      return
    }

    val unresolvedContexts = session.snapshotUnresolvedFiles()
      .mapNotNull { file -> session.promptFileContexts[file] }
      .sortedBy(AgentVcsMergePromptFileContext::projectRelativePath)
    if (unresolvedContexts.isEmpty()) {
      disposeSession(session)
      notifySuccess(AgentVcsMergeBundle.message("merge.agent.resolve.launch.success.auto.resolved"))
      return
    }

    val initialMessageRequest = AgentPromptInitialMessageRequest(
      prompt = AgentVcsMergeSessionSupport.buildInitialPrompt(),
      projectPath = projectPath,
      contextItems = AgentVcsMergeSessionSupport.buildContextItems(unresolvedContexts),
    )
    serviceAsync<AgentSessionLaunchService>().createNewSession(
      path = projectPath,
      provider = session.agentProvider,
      mode = session.launchMode,
      entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
      currentProject = project,
      initialMessageRequest = initialMessageRequest,
      preferredDedicatedFrame = false,
      openedChatHandler = { _, file ->
        session.threadFile = file
        pinThread(file)
        serviceAsync<AgentSessionUiPreferencesStateService>()
          .updateVcsMergeProviderPreferencesOnLaunch(session.agentProvider, session.launchMode)
      },
      promptLaunchResolved = { result ->
        if (!result.launched) {
          disposeSession(session)
          showError(result.asMessage())
        }
      },
      singleFlightDiscriminator = session.sessionId,
      updateGeneralProviderPreferences = false,
      threadTitle = AgentVcsMergeBundle.message("merge.agent.thread.title"),
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
    session.removeUnresolvedFiles(files)
    val resolution = MergeSession.Resolution.Merged
    val mergeSession = session.mergeSession
    if (mergeSession is MergeSessionEx) {
      mergeSession.conflictResolvedForFiles(files, resolution)
    }
    else if (mergeSession != null) {
      files.forEach { file ->
        mergeSession.conflictResolvedForFile(file, resolution)
      }
    }
    else {
      files.forEach { file ->
        session.mergeProvider.conflictResolvedForFile(file)
      }
    }

    VcsDirtyScopeManager.getInstance(project).filesDirty(files, emptyList())
  }

  private fun buildSessionKey(files: List<VirtualFile>): String {
    return files
      .map { file -> FileUtil.toSystemIndependentName(file.path) }
      .sorted()
      .joinToString(separator = "\u0000")
  }

  private fun showError(message: @Nls String) {
    coroutineScope.launch(Dispatchers.UiWithModelAccess) {
      Messages.showErrorDialog(project, message, AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.title"))
    }
  }

  private fun notifySuccess(message: @Nls String) {
    coroutineScope.launch(Dispatchers.UiWithModelAccess) {
      VcsNotifier.getInstance(project).notifySuccess(AUTO_RESOLVED_NOTIFICATION_ID, "", message)
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
    private const val AUTO_RESOLVED_NOTIFICATION_ID = "agent.merge.auto.resolved"
  }
}

private fun ActiveAgentVcsMergeSession.snapshotUnresolvedFiles(): List<VirtualFile> {
  synchronized(unresolvedFiles) {
    return unresolvedFiles.toList()
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

private fun createMergeRequest(
  project: Project,
  file: VirtualFile,
  requestFactory: DiffRequestFactory,
  mergeProvider: MergeProvider,
  conflictData: ConflictData,
): MergeRequest {
  val mergeData = conflictData.mergeData
  val byteContents = listOf(mergeData.CURRENT, mergeData.ORIGINAL, mergeData.LAST)

  return if (mergeProvider.isBinary(file)) {
    requestFactory.createBinaryMergeRequest(project, file, byteContents, conflictData.title, conflictData.contentTitles, null)
  }
  else {
    requestFactory.createMergeRequest(
      project,
      file,
      byteContents,
      mergeData.CONFLICT_TYPE,
      conflictData.title,
      conflictData.contentTitles,
      null
    )
  }.also { request ->
    MergeUtils.putRevisionInfos(request, mergeData)
    conflictData.contentTitleCustomizers.run {
      DiffUtil.addTitleCustomizers(request, listOf(leftTitleCustomizer, centerTitleCustomizer, rightTitleCustomizer))
    }
  }
}

private suspend fun loadConflictData(
  file: VirtualFile,
  mergeProvider: MergeProvider,
  mergeDialogCustomizer: MergeDialogCustomizer,
): ConflictData {
  val filePath = VcsUtil.getFilePath(file)
  val mergeData = withContext(Dispatchers.IO) {
    mergeProvider.loadRevisions(file)
  }

  val title = tryCompute { mergeDialogCustomizer.getMergeWindowTitle(file) }
  val conflictTitles = listOf(
    tryCompute { mergeDialogCustomizer.getLeftPanelTitle(file) },
    tryCompute { mergeDialogCustomizer.getCenterPanelTitle(file) },
    tryCompute { mergeDialogCustomizer.getRightPanelTitle(file, mergeData.LAST_REVISION_NUMBER) },
  )
  val titleCustomizer = tryCompute { mergeDialogCustomizer.getTitleCustomizerList(filePath) }
                        ?: MergeDialogCustomizer.DEFAULT_CUSTOMIZER_LIST
  return ConflictData(mergeData, title, conflictTitles, titleCustomizer)
}

private fun buildPromptFileContext(
  project: Project,
  file: VirtualFile,
  conflictData: ConflictData,
  snapshot: MergeConflictAiFileSnapshot?,
  isBinary: Boolean,
): AgentVcsMergePromptFileContext {
  val mergeData = conflictData.mergeData
  return AgentVcsMergePromptFileContext(
    projectRelativePath = file.toProjectRelativePath(project),
    binary = isBinary,
    totalConflicts = snapshot?.totalConflicts,
    resolvedConflicts = snapshot?.resolvedConflicts,
    unresolvedConflicts = snapshot?.unresolvedConflicts,
    yoursTitle = conflictData.contentTitles.getOrNull(0),
    baseTitle = conflictData.contentTitles.getOrNull(1),
    theirsTitle = conflictData.contentTitles.getOrNull(2),
    yoursRevision = mergeData.CURRENT_REVISION_NUMBER?.asString(),
    baseRevision = mergeData.ORIGINAL_REVISION_NUMBER?.asString(),
    theirsRevision = mergeData.LAST_REVISION_NUMBER?.asString(),
  )
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

private fun <T> tryCompute(task: () -> T): T? {
  try {
    return task()
  }
  catch (e: ProcessCanceledException) {
    throw e
  }
  catch (e: VcsException) {
    LOG.warn(e)
  }
  catch (e: Exception) {
    LOG.error(e)
  }
  return null
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

private data class ConflictData(
  @JvmField val mergeData: MergeData,
  @JvmField val title: @NlsContexts.DialogTitle String?,
  @JvmField val contentTitles: List<@NlsContexts.Label String?>,
  @JvmField val contentTitleCustomizers: MergeDialogCustomizer.DiffEditorTitleCustomizerList,
)

@Internal
object AgentVcsMergeSessionSupport {
  fun buildInitialPrompt(): String {
    return buildString {
      appendLine("Resolve the current merge conflicts for this IntelliJ IDEA worktree.")
      appendLine("Use normal IDE tools, git workflow, file edits, and any installed skills.")
      appendLine("Success means every conflicted file leaves VCS conflict state for this merge.")
      appendLine(
        "If this worktree is in the middle of a Git merge, rebase, or cherry-pick, stage resolved files and continue that operation when needed.",
      )
      append("Ask follow-up questions in this thread if the intended merge result is unclear.")
    }
  }

  fun buildContextItems(fileContexts: List<AgentVcsMergePromptFileContext>): List<AgentPromptContextItem> {
    if (fileContexts.isEmpty()) return emptyList()

    val orderedContexts = fileContexts.sortedBy(AgentVcsMergePromptFileContext::projectRelativePath)
    return buildList {
      add(buildSummaryContextItem(orderedContexts))
      orderedContexts.forEach { fileContext ->
        add(buildFileContextItem(fileContext))
      }
    }
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

private fun buildSummaryContextItem(
  fileContexts: List<AgentVcsMergePromptFileContext>,
): AgentPromptContextItem {
  val first = fileContexts.first()
  val body = buildString {
    appendLine("Conflicted files: ${fileContexts.size}")
    appendContextLine(this, "Yours", formatRevisionSummary(first.yoursRevision, first.yoursTitle))
    appendContextLine(this, "Base", formatRevisionSummary(first.baseRevision, first.baseTitle))
    appendContextLine(this, "Theirs", formatRevisionSummary(first.theirsRevision, first.theirsTitle))
  }.trimEnd()
  return AgentPromptContextItem(
    rendererId = AgentPromptContextRendererIds.SNIPPET,
    title = "Merge Session",
    body = body,
    payload = AgentPromptPayload.obj(
      "fileCount" to AgentPromptPayload.num(fileContexts.size),
    ),
    itemId = "vcsMerge.session",
    source = "vcsMerge",
    truncation = AgentPromptContextTruncation.none(body.length),
  )
}

private fun formatRevisionSummary(revision: String?, title: String?): String? {
  if (revision == null && title == null) return null
  return buildString {
    if (revision != null) append(revision)
    if (title != null) {
      if (revision != null) append(" ")
      append("($title)")
    }
  }
}

private fun buildFileContextItem(fileContext: AgentVcsMergePromptFileContext): AgentPromptContextItem {
  val body = buildString {
    appendLine("Path: ${fileContext.projectRelativePath}")
    if (fileContext.binary) {
      appendLine("Binary conflict: requires manual or VCS-specific workflow.")
    }
    if (fileContext.totalConflicts != null && fileContext.resolvedConflicts != null && fileContext.unresolvedConflicts != null) {
      appendLine(
        "Conflict counts: total=${fileContext.totalConflicts} resolved=${fileContext.resolvedConflicts} unresolved=${fileContext.unresolvedConflicts}",
      )
    }
  }.trimEnd()
  return AgentPromptContextItem(
    rendererId = AgentPromptContextRendererIds.SNIPPET,
    title = "Merge File: ${fileContext.projectRelativePath}",
    body = body,
    payload = AgentPromptPayload.obj(
      "path" to AgentPromptPayload.str(fileContext.projectRelativePath),
      "binary" to AgentPromptPayload.bool(fileContext.binary),
    ),
    itemId = "vcsMerge.file.${fileContext.projectRelativePath}",
    source = "vcsMerge",
    truncation = AgentPromptContextTruncation.none(body.length),
  )
}

private fun appendContextLine(builder: StringBuilder, label: String, value: String?) {
  if (!value.isNullOrBlank()) {
    builder.appendLine("$label: $value")
  }
}

private val LOG = Logger.getInstance(AgentVcsMergeSessionService::class.java)

internal fun VirtualFile.toProjectRelativePath(project: Project): String {
  val basePath = project.basePath ?: return FileUtil.toSystemIndependentName(path)
  return FileUtil.getRelativePath(basePath, path, '/') ?: FileUtil.toSystemIndependentName(path)
}

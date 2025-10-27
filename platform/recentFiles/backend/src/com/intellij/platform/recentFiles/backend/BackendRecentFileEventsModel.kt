// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.HighlightingPassesCache
import com.intellij.ide.actions.shouldUseFallbackSwitcher
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl.RecentFileHistoryOrderListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.recentFiles.shared.*
import com.intellij.problems.ProblemListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.Icon
import kotlin.time.Duration.Companion.milliseconds

private val LOG by lazy { fileLogger() }

@Service(Service.Level.PROJECT)
internal class BackendRecentFileEventsModel(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val bufferSize = Registry.intValue("editor.navigation.history.stack.size").coerceIn(100, 1000)
  private val updateDebounceMs = Registry.intValue("switcher.presentation.update.debounce.interval.ms").coerceIn(0, 10000)

  private val orderChangeEvents = Channel<OrderChangeEvent>(capacity = UNLIMITED)
  private val fileChangeEvents = Channel<List<VirtualFile>>(capacity = UNLIMITED)

  private val recentlyOpenedFiles = MutableSharedFlow<BackendRecentFilesEvent>(
    extraBufferCapacity = bufferSize,
    replay = bufferSize,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  private val recentlyEditedFiles = MutableSharedFlow<BackendRecentFilesEvent>(
    extraBufferCapacity = bufferSize,
    replay = bufferSize,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  private val recentlyOpenedUnpinnedFiles = MutableSharedFlow<BackendRecentFilesEvent>(
    extraBufferCapacity = bufferSize,
    replay = bufferSize,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  init {
    // Workaround for `disposed temporary` state that coroutines do not respect when being launched inside project service scope.
    // The active subscription leads to coroutine A launched during test A being executed during test B or in between (!) and producing various
    // `already disposed` and alike exceptions. It needs to be fixed on the platform side,
    // maybe by cancelling project service scope' children during temporary dispose phase
    if (!ApplicationManager.getApplication().isUnitTestMode
        && project is ProjectEx
        && !shouldUseFallbackSwitcher()) {
      project.messageBus.connect(coroutineScope).apply {
        subscribe(RecentFileHistoryOrderListener.TOPIC, ChangedIdeHistoryFileHistoryOrderListener(project))
        subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, RecentFilesDaemonAnalyserListener(project))
        subscribe(FileStatusListener.TOPIC, RecentFilesVcsStatusListener(project))
        subscribe(ProblemListener.TOPIC, RecentFilesProblemsListener(project))
      }
    }

    coroutineScope.launch {
      processOrderChangeEvents()
    }

    coroutineScope.launch {
      processFileUpdateEvents()
    }
  }

  fun getRecentFiles(fileKind: RecentFileKind): Flow<BackendRecentFilesEvent> {
    LOG.debug("Switcher get recent files for kind: $fileKind")
    return chooseTargetFlow(fileKind)
  }

  suspend fun emitRecentFilesMetadata(metadataRequest: RecentFilesBackendRequest.FetchMetadata) {
    LOG.debug("Switcher emit recent files metadata: $metadataRequest")
    val targetFlow = chooseTargetFlow(metadataRequest.filesKind)

    val metadata = readAction {
      metadataRequest.frontendRecentFiles
        .mapNotNull { frontendFileId -> frontendFileId.virtualFile() }
        .filter { virtualFile -> virtualFile.isValid }
        .map { frontendFile -> createRecentFileViewModel(frontendFile, project) }
    }

    val event = if (metadataRequest.forceAddToModel)
      BackendRecentFilesEvent.ItemsAdded(metadata)
    else
      BackendRecentFilesEvent.ItemsUpdated(metadata, false)

    targetFlow.emit(event)
  }

  suspend fun emitRecentFiles(searchRequest: RecentFilesBackendRequest.FetchFiles) {
    LOG.debug("Switcher emit recent files: $searchRequest")
    val targetFlow = chooseTargetFlow(searchRequest.filesKind)

    targetFlow.emit(BackendRecentFilesEvent.AllItemsRemoved())
    val freshRecentFiles = collectRecentFiles(searchRequest)
    if (freshRecentFiles != null) {
      targetFlow.emit(freshRecentFiles)
    }
  }

  fun scheduleApplyBackendChanges(changeKind: FileChangeKind, files: Collection<VirtualFile>) {
    if (files.isEmpty()) return
    val reasonablyLimitedFilesList = files.take(bufferSize)

    LOG.debug { "Switcher emit file update initiated by backend, file: $reasonablyLimitedFilesList, change kind: ${changeKind}, project: $project" }
    when (changeKind) {
      FileChangeKind.UPDATED -> {
        fileChangeEvents.trySend(reasonablyLimitedFilesList)
      }
      else -> {
        orderChangeEvents.trySend(OrderChangeEvent(changeKind, reasonablyLimitedFilesList))
      }
    }
  }

  private suspend fun processOrderChangeEvents(): Nothing {
    orderChangeEvents.consumeEach { event ->
      LOG.runAndLogException {
        processOrderChangeEvent(event)
      }
    }
    awaitCancellation() // unreachable
  }

  private suspend fun processFileUpdateEvents(): Nothing {
    while (true) {
      delay(updateDebounceMs.milliseconds)

      val pendingFiles = mutableSetOf<VirtualFile>()
      pendingFiles += fileChangeEvents.receive()

      while (true) {
        pendingFiles += fileChangeEvents.tryReceive().getOrNull() ?: break
      }

      LOG.runAndLogException {
        processFileUpdateEvent(pendingFiles.toList(), putOnTop = false)
      }
    }
  }

  private suspend fun processOrderChangeEvent(event: OrderChangeEvent) {
    when (event.changeKind) {
      FileChangeKind.ADDED -> {
        val models = createRecentFilesViewModels(event.files)
        val fileEvent = BackendRecentFilesEvent.ItemsAdded(models)

        for (fileKind in RecentFileKind.entries) {
          chooseTargetFlow(fileKind).emit(fileEvent)
        }
      }
      FileChangeKind.REMOVED -> {
        val fileEvent = BackendRecentFilesEvent.ItemsRemoved(event.files)

        for (fileKind in RecentFileKind.entries) {
          chooseTargetFlow(fileKind).emit(fileEvent)
        }
      }
      FileChangeKind.UPDATED_AND_PUT_ON_TOP -> {
        processFileUpdateEvent(event.files, putOnTop = true)
      }
      FileChangeKind.UPDATED -> {
        LOG.error("Unexpected change kind: ${event.changeKind}")
        return
      }
    }
  }

  private suspend fun processFileUpdateEvent(files: List<VirtualFile>, putOnTop: Boolean = true) {
    val knownFilesByKind = RecentFileKind.entries.associateWith { fileKind ->
      BackendRecentFilesModel.getInstance(project).getFilesByKind(fileKind).toSet()
    }

    val filesToUpdate = files.filter { file -> knownFilesByKind.values.any { known -> known.contains(file) } }

    val models = createRecentFilesViewModels(filesToUpdate)
    assert(models.size == filesToUpdate.size)

    for (fileKind in RecentFileKind.entries) {
      val knownFiles = knownFilesByKind[fileKind]!!
      val eventModels = models.filterIndexed { index, _ -> knownFiles.contains(filesToUpdate[index]) }

      val fileEvent = BackendRecentFilesEvent.ItemsUpdated(eventModels, putOnTop)
      chooseTargetFlow(fileKind).emit(fileEvent)
    }
  }

  private suspend fun createRecentFilesViewModels(files: List<VirtualFile>): List<BackendRecentFilePresentation> {
    return files.map {
      readAction {
        createRecentFileViewModel(it, project)
      }
    }
  }

  suspend fun hideAlreadyShownFiles(hideFilesRequest: RecentFilesBackendRequest.HideFiles) {
    LOG.debug("Switcher hide file: $hideFilesRequest")

    if (hideFilesRequest.filesKind == RecentFileKind.RECENTLY_OPENED) {
      val virtualFiles = hideFilesRequest.filesToHide.mapNotNull(VirtualFileId::virtualFile)
      for (file in virtualFiles) {
        EditorHistoryManager.getInstance(project).removeFile(file)
      }
    }
    chooseTargetFlow(hideFilesRequest.filesKind)
      .emit(BackendRecentFilesEvent.ItemsRemoved(hideFilesRequest.filesToHide.mapNotNull { it.virtualFile() }))
  }

  fun scheduleRehighlightUnopenedFiles() {
    LOG.debug("Switcher rehighlight files in project: $project")
    HighlightingPassesCache.getInstance(project).schedule(getNotOpenedRecentFiles(project))
  }

  private fun getNotOpenedRecentFiles(project: Project): List<VirtualFile> {
    val recentFiles = EditorHistoryManager.getInstance(project).fileList
    val openFiles = FileEditorManager.getInstance(project).openFiles
    return recentFiles.subtract(openFiles.toSet()).toList()
  }

  private suspend fun collectRecentFiles(filter: RecentFilesBackendRequest.FetchFiles): BackendRecentFilesEvent? {
    LOG.debug("Switcher started fetching recent files")
    val project = filter.projectId.findProjectOrNull() ?: return null

    val collectedFiles = readAction {
      getFilesToShow(project = project,
                     recentFileKind = filter.filesKind,
                     filesFromFrontendEditorSelectionHistory = filter.frontendEditorSelectionHistory.mapNotNull(VirtualFileId::virtualFile))
        .map { createRecentFileViewModel(it, project) }
    }
    LOG.debug("Switcher collected ${collectedFiles.size} recent files")
    LOG.trace { "Switcher collected recent files list: ${collectedFiles.joinToString(prefix = "\n", separator = "\n") { it.mainText }}" }

    return BackendRecentFilesEvent.ItemsAdded(collectedFiles)
  }

  private fun chooseTargetFlow(fileKind: RecentFileKind): MutableSharedFlow<BackendRecentFilesEvent> {
    return when (fileKind) {
      RecentFileKind.RECENTLY_OPENED -> recentlyOpenedFiles
      RecentFileKind.RECENTLY_EDITED -> recentlyEditedFiles
      RecentFileKind.RECENTLY_OPENED_UNPINNED -> recentlyOpenedUnpinnedFiles
    }
  }

  companion object {
    fun getInstance(project: Project): BackendRecentFileEventsModel {
      return project.service<BackendRecentFileEventsModel>()
    }

    suspend fun getInstanceAsync(project: Project): BackendRecentFileEventsModel {
      return project.serviceAsync<BackendRecentFileEventsModel>()
    }
  }
}

private data class OrderChangeEvent(val changeKind: FileChangeKind, val files: List<VirtualFile>)


@ApiStatus.Internal
internal sealed interface BackendRecentFilesEvent {
  class ItemsUpdated(val batch: List<BackendRecentFilePresentation>, val putOnTop: Boolean) : BackendRecentFilesEvent
  class ItemsAdded(val batch: List<BackendRecentFilePresentation>) : BackendRecentFilesEvent
  class ItemsRemoved(val batch: List<VirtualFile>) : BackendRecentFilesEvent
  class AllItemsRemoved : BackendRecentFilesEvent
}

internal class BackendRecentFilePresentation(
  val mainText: @NlsSafe String,
  val statusText: @NlsSafe String,
  val pathText: @NlsSafe String,
  val hasProblems: Boolean,
  val icon: Icon,
  val foregroundTextColor: Color?,
  val backgroundColor: Color?,
  val virtualFile: VirtualFile,
)

@ApiStatus.Internal
internal fun BackendRecentFilesEvent.toRpcModel(): RecentFilesEvent = when (this) {
  is BackendRecentFilesEvent.ItemsUpdated -> RecentFilesEvent.ItemsUpdated(batch.map { it.toRpcModel() }, putOnTop)
  is BackendRecentFilesEvent.ItemsAdded -> RecentFilesEvent.ItemsAdded(batch.map { it.toRpcModel() })
  is BackendRecentFilesEvent.ItemsRemoved -> RecentFilesEvent.ItemsRemoved(batch.map { it.rpcId() })
  is BackendRecentFilesEvent.AllItemsRemoved -> RecentFilesEvent.AllItemsRemoved()
}

internal fun BackendRecentFilePresentation.toRpcModel(): SwitcherRpcDto {
  return SwitcherRpcDto.File(
    mainText = mainText,
    statusText = statusText,
    pathText = pathText,
    hasProblems = hasProblems,
    iconId = icon.rpcId(),
    foregroundTextColorId = foregroundTextColor?.rpcId(),
    backgroundColorId = backgroundColor?.rpcId(),
    virtualFileId = virtualFile.rpcId()
  )
}

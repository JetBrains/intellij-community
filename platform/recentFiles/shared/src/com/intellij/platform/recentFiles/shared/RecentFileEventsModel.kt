// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.codeInsight.daemon.HighlightingPassesCache
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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.findProjectOrNull
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
import java.awt.Color
import javax.swing.Icon
import kotlin.time.Duration.Companion.milliseconds

private val LOG by lazy { fileLogger() }

/**
 * The recent files model of this process: one event flow per [RecentFileKind], and the presentation of each file.
 *
 * [RecentFileEventsController] feeds it, [RecentFilesModel] mirrors the resulting lists back, and [FileSwitcherApiImpl]
 * serves the flows to the user interface. Only a process that hosts the model runs it, see
 * [doesProcessHostRecentFilesModel].
 */
@Service(Service.Level.PROJECT)
internal class RecentFileEventsModel(private val project: Project, coroutineScope: CoroutineScope) {
  private val bufferSize = Registry.intValue("editor.navigation.history.stack.size").coerceIn(100, 1000)
  private val updateDebounceMs = Registry.intValue("switcher.presentation.update.debounce.interval.ms").coerceIn(0, 10000)

  private val orderChangeEvents = Channel<OrderChangeEvent>(capacity = UNLIMITED)
  private val fileChangeEvents = Channel<List<VirtualFile>>(capacity = UNLIMITED)

  private val recentlyOpenedFiles = MutableSharedFlow<LocalRecentFilesEvent>(
    extraBufferCapacity = bufferSize,
    replay = bufferSize,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  private val recentlyEditedFiles = MutableSharedFlow<LocalRecentFilesEvent>(
    extraBufferCapacity = bufferSize,
    replay = bufferSize,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  private val recentlyOpenedUnpinnedFiles = MutableSharedFlow<LocalRecentFilesEvent>(
    extraBufferCapacity = bufferSize,
    replay = bufferSize,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  init {
    // The other listeners of the model are declarative, see the descriptors of the shared and of the backend module.
    // This one stays programmatic because of the workaround below.
    //
    // Workaround for `disposed temporary` state that coroutines do not respect when being launched inside project service scope.
    // The active subscription leads to coroutine A launched during test A being executed during test B or in between (!) and producing various
    // `already disposed` and alike exceptions. It needs to be fixed on the platform side,
    // maybe by cancelling project service scope' children during temporary dispose phase
    if (!ApplicationManager.getApplication().isUnitTestMode && project is ProjectEx) {
      project.messageBus.connect(coroutineScope).apply {
        subscribe(RecentFileHistoryOrderListener.TOPIC, ChangedIdeHistoryFileHistoryOrderListener(project))
      }
    }

    coroutineScope.launch {
      processOrderChangeEvents()
    }

    coroutineScope.launch {
      processFileUpdateEvents()
    }
  }

  fun getRecentFiles(fileKind: RecentFileKind): Flow<LocalRecentFilesEvent> {
    LOG.debug("Switcher get recent files for kind: $fileKind")
    return chooseTargetFlow(fileKind)
  }

  suspend fun emitRecentFilesMetadata(metadataRequest: RecentFilesBackendRequest.FetchMetadata) {
    LOG.debug("Switcher emit recent files metadata: $metadataRequest")
    val targetFlow = chooseTargetFlow(metadataRequest.filesKind)

    val metadata =
      metadataRequest.frontendRecentFiles
        .mapNotNull { frontendFileId -> frontendFileId.virtualFile() }
        .filter { isAllowedInRecentFilesModel(project, metadataRequest.filesKind, it) }
        .map { frontendFile ->
          readAction {
            createRecentFileViewModel(frontendFile, project)
          }
        }

    val event = if (metadataRequest.forceAddToModel)
      LocalRecentFilesEvent.ItemsAdded(metadata)
    else
      LocalRecentFilesEvent.ItemsUpdated(metadata, false)

    targetFlow.emit(event)
  }

  suspend fun emitRecentFiles(searchRequest: RecentFilesBackendRequest.FetchFiles) {
    LOG.debug("Switcher emit recent files: $searchRequest")
    EditorHistoryManager.preloadHistory(project)

    val targetFlow = chooseTargetFlow(searchRequest.filesKind)

    targetFlow.emit(LocalRecentFilesEvent.AllItemsRemoved())

    val freshRecentFiles = collectRecentFiles(searchRequest)
    if (freshRecentFiles != null) {
      targetFlow.emit(freshRecentFiles)
    }
  }

  fun scheduleApplyChanges(changeKind: FileChangeKind, files: Collection<VirtualFile>) {
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
        for (fileKind in RecentFileKind.entries) {
          val models = createRecentFilesViewModels(
            event.files.filter { isAllowedInRecentFilesModel(project, fileKind, it) }
          )
          val fileEvent = LocalRecentFilesEvent.ItemsAdded(models)
          chooseTargetFlow(fileKind).emit(fileEvent)
        }
      }
      FileChangeKind.REMOVED -> {
        val fileEvent = LocalRecentFilesEvent.ItemsRemoved(event.files)

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
      RecentFilesModel.getInstance(project).getFilesByKind(fileKind).toSet()
    }

    val filesToUpdate = files.filter { file -> knownFilesByKind.values.any { known -> known.contains(file) } }

    for (fileKind in RecentFileKind.entries) {
      val knownFiles = knownFilesByKind[fileKind]!!
      val filesForKind = filesToUpdate.filter { file ->
        knownFiles.contains(file) && isAllowedInRecentFilesModel(project, fileKind, file)
      }
      val eventModels = createRecentFilesViewModels(filesForKind)

      val fileEvent = LocalRecentFilesEvent.ItemsUpdated(eventModels, putOnTop)
      chooseTargetFlow(fileKind).emit(fileEvent)
    }
  }

  private suspend fun createRecentFilesViewModels(files: List<VirtualFile>): List<LocalRecentFilePresentation> {
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
      .emit(LocalRecentFilesEvent.ItemsRemoved(hideFilesRequest.filesToHide.mapNotNull { it.virtualFile() }))
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

  private suspend fun collectRecentFiles(filter: RecentFilesBackendRequest.FetchFiles): LocalRecentFilesEvent? {
    LOG.debug("Switcher started fetching recent files")
    val project = filter.projectId.findProjectOrNull() ?: return null

    val collectedFiles =
      getFilesToShow(project = project,
                     recentFileKind = filter.filesKind,
                     filesFromFrontendEditorSelectionHistory = filter.frontendEditorSelectionHistory.mapNotNull(VirtualFileId::virtualFile))
        .filter { isAllowedInRecentFilesModel(project, filter.filesKind, it) }
        .map {
          readAction {
            createRecentFileViewModel(it, project)
          }
        }
    LOG.debug("Switcher collected ${collectedFiles.size} recent files")
    LOG.trace { "Switcher collected recent files list: ${collectedFiles.joinToString(prefix = "\n", separator = "\n") { it.mainText }}" }

    return LocalRecentFilesEvent.ItemsAdded(collectedFiles)
  }

  private fun chooseTargetFlow(fileKind: RecentFileKind): MutableSharedFlow<LocalRecentFilesEvent> {
    return when (fileKind) {
      RecentFileKind.RECENTLY_OPENED -> recentlyOpenedFiles
      RecentFileKind.RECENTLY_EDITED -> recentlyEditedFiles
      RecentFileKind.RECENTLY_OPENED_UNPINNED -> recentlyOpenedUnpinnedFiles
    }
  }

  companion object {
    fun getInstance(project: Project): RecentFileEventsModel {
      return project.service<RecentFileEventsModel>()
    }

    suspend fun getInstanceAsync(project: Project): RecentFileEventsModel {
      return project.serviceAsync<RecentFileEventsModel>()
    }
  }
}

private data class OrderChangeEvent(val changeKind: FileChangeKind, val files: List<VirtualFile>)

internal sealed interface LocalRecentFilesEvent {
  class ItemsUpdated(val batch: List<LocalRecentFilePresentation>, val putOnTop: Boolean) : LocalRecentFilesEvent
  class ItemsAdded(val batch: List<LocalRecentFilePresentation>) : LocalRecentFilesEvent
  class ItemsRemoved(val batch: List<VirtualFile>) : LocalRecentFilesEvent
  class AllItemsRemoved : LocalRecentFilesEvent
}

internal class LocalRecentFilePresentation(
  val mainText: @NlsSafe String,
  val statusText: @NlsSafe String,
  val pathText: @NlsSafe String,
  val hasProblems: Boolean,
  val icon: Icon,
  val foregroundTextColor: Color?,
  val backgroundColor: Color?,
  val virtualFile: VirtualFile,
)

internal fun LocalRecentFilesEvent.toRpcModel(): RecentFilesEvent = when (this) {
  is LocalRecentFilesEvent.ItemsUpdated -> RecentFilesEvent.ItemsUpdated(batch.map { it.toRpcModel() }, putOnTop)
  is LocalRecentFilesEvent.ItemsAdded -> RecentFilesEvent.ItemsAdded(batch.map { it.toRpcModel() })
  is LocalRecentFilesEvent.ItemsRemoved -> RecentFilesEvent.ItemsRemoved(batch.map { it.rpcId() })
  is LocalRecentFilesEvent.AllItemsRemoved -> RecentFilesEvent.AllItemsRemoved()
}

internal fun LocalRecentFilePresentation.toRpcModel(): SwitcherRpcDto {
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

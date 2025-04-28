// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.HighlightingPassesCache
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl.RecentFileHistoryOrderListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.platform.recentFiles.shared.RecentFilesBackendRequest
import com.intellij.platform.recentFiles.shared.RecentFilesEvent
import com.intellij.problems.ProblemListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

private val LOG by lazy { fileLogger() }

@Service(Service.Level.PROJECT)
internal class BackendRecentFileEventsModel(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val bufferSize = Registry.intValue("editor.navigation.history.stack.size").coerceIn(100, 1000)

  private val recentlyOpenedFiles = MutableSharedFlow<RecentFilesEvent>(
    extraBufferCapacity = bufferSize,
    replay = bufferSize,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  private val recentlyEditedFiles = MutableSharedFlow<RecentFilesEvent>(
    extraBufferCapacity = bufferSize,
    replay = bufferSize,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  private val recentlyOpenedUnpinnedFiles = MutableSharedFlow<RecentFilesEvent>(
    extraBufferCapacity = bufferSize,
    replay = bufferSize,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  init {
    // Workaround for `disposed temporary` state that coroutines do not respect when being launched inside project service scope.
    // The active subscription leads to coroutine A launched during test A being executed during test B or in between (!) and producing various
    // `already disposed` and alike exceptions. It needs to be fixed on the platform side,
    // maybe by cancelling project service scope' children during temporary dispose phase
    if (!ApplicationManager.getApplication().isUnitTestMode && project is ProjectEx) {
      project.messageBus.connect(coroutineScope).apply {
        subscribe(RecentFileHistoryOrderListener.TOPIC, ChangedIdeHistoryFileHistoryOrderListener(project))
        subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, RecentFilesDaemonAnalyserListener(project))
        subscribe(FileStatusListener.TOPIC, RecentFilesVcsStatusListener(project))
        subscribe(ProblemListener.TOPIC, RecentFilesProblemsListener(project))
      }
    }
  }

  fun getRecentFiles(fileKind: RecentFileKind): Flow<RecentFilesEvent> {
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
    targetFlow.emit(RecentFilesEvent.ItemsUpdated(metadata, false))
  }

  suspend fun emitRecentFiles(searchRequest: RecentFilesBackendRequest.FetchFiles) {
    LOG.debug("Switcher emit recent files: $searchRequest")
    val targetFlow = chooseTargetFlow(searchRequest.filesKind)

    targetFlow.emit(RecentFilesEvent.AllItemsRemoved())
    val freshRecentFiles = collectRecentFiles(searchRequest)
    if (freshRecentFiles != null) {
      targetFlow.emit(freshRecentFiles)
    }
  }

  fun scheduleApplyBackendChangesToAllFileKinds(changeKind: FileChangeKind, files: List<VirtualFile>) {
    val reasonablyLimitedFilesList = files.take(bufferSize)
    for (fileKind in RecentFileKind.entries) {
      scheduleApplyBackendChanges(fileKind, changeKind, reasonablyLimitedFilesList)
    }
  }

  fun emitUncertainChange() {
    for (fileKind in RecentFileKind.entries) {
      chooseTargetFlow(fileKind).tryEmit(RecentFilesEvent.UncertainChangeOccurred())
    }
  }

  fun scheduleApplyBackendChanges(fileKind: RecentFileKind, changeKind: FileChangeKind, files: List<VirtualFile>) {
    coroutineScope.launch {
      LOG.debug("Switcher emit file update initiated by backend, file: $files, change kind: ${changeKind}, project: $project")
      val fileEvent = when (changeKind) {
        FileChangeKind.ADDED -> {
          val models = readAction {
            files.map { createRecentFileViewModel(it, project) }
          }
          RecentFilesEvent.ItemsAdded(models)
        }
        FileChangeKind.UPDATED -> {
          val models = readAction {
            files.map { createRecentFileViewModel(it, project) }
          }
          RecentFilesEvent.ItemsUpdated(models, false)
        }
        FileChangeKind.UPDATED_AND_PUT_ON_TOP -> {
          val models = readAction {
            files.map { createRecentFileViewModel(it, project) }
          }
          RecentFilesEvent.ItemsUpdated(models, true)
        }
        FileChangeKind.REMOVED -> {
          RecentFilesEvent.ItemsRemoved(files.map { it.rpcId() })
        }
      }

      chooseTargetFlow(fileKind).emit(fileEvent)
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
      .emit(RecentFilesEvent.ItemsRemoved(hideFilesRequest.filesToHide))
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

  private suspend fun collectRecentFiles(filter: RecentFilesBackendRequest.FetchFiles): RecentFilesEvent? {
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

    return RecentFilesEvent.ItemsAdded(collectedFiles)
  }

  private fun chooseTargetFlow(fileKind: RecentFileKind): MutableSharedFlow<RecentFilesEvent> {
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

internal enum class FileChangeKind {
  REMOVED, ADDED, UPDATED, UPDATED_AND_PUT_ON_TOP
}
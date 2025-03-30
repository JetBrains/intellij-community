// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.codeInsight.daemon.HighlightingPassesCache
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.platform.recentFiles.shared.RecentFilesBackendRequest
import com.intellij.platform.recentFiles.shared.RecentFilesEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

private val LOG by lazy { fileLogger() }

@Service(Service.Level.PROJECT)
internal class BackendRecentFilesModel(private val project: Project, private val coroutineScope: CoroutineScope) {
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
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      project.messageBus.connect(coroutineScope)
        .subscribe(IdeDocumentHistoryImpl.RecentPlacesListener.TOPIC, ChangedFilesVfsListener(project))
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
    targetFlow.emit(RecentFilesEvent.ItemsUpdated(metadata))
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

  val count = AtomicInteger()

  fun applyBackendChanges(fileKind: RecentFileKind, file: VirtualFile, isAdded: Boolean) {
    val n = count.incrementAndGet()
    println("Count before: $n")
    coroutineScope.launch {
      Thread.sleep(30)
      println("Count after: ${n}")
      LOG.debug("Switcher emit file update initiated by backend, file: $file, isAdded: ${isAdded}, project: $project")
      val fileEvent = if (isAdded) {
        val fileModel = readAction {
          println("Count readlok $n")
          createRecentFileViewModel(file, project)
        }
        RecentFilesEvent.ItemsAdded(listOf(fileModel))
      }
      else {
        RecentFilesEvent.ItemsRemoved(listOf(file.rpcId()))
      }

      chooseTargetFlow(fileKind).emit(fileEvent)
    }
  }

  suspend fun hideAlreadyShownFiles(hideFilesRequest: RecentFilesBackendRequest.HideFiles) {
    LOG.debug("Switcher hide file: $hideFilesRequest")

    val targetFlow = chooseTargetFlow(hideFilesRequest.filesKind)
    val project = hideFilesRequest.projectId.findProjectOrNull() ?: return
    val virtualFiles = hideFilesRequest.filesToHide.mapNotNull(VirtualFileId::virtualFile)
    for (file in virtualFiles) {
      EditorHistoryManager.getInstance(project).removeFile(file)
    }
    targetFlow.emit(RecentFilesEvent.ItemsRemoved(hideFilesRequest.filesToHide))
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
    fun getInstance(project: Project): BackendRecentFilesModel {
      return project.service<BackendRecentFilesModel>()
    }

    suspend fun getInstanceAsync(project: Project): BackendRecentFilesModel {
      return project.serviceAsync<BackendRecentFilesModel>()
    }
  }
}
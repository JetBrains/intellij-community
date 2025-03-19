// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("removal", "ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.vcs.impl

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.ChangeList
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.vcsUtil.VcsUtil
import it.unimi.dsi.fastutil.objects.Object2BooleanMap
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import java.util.concurrent.TimeUnit

@VisibleForTesting
@Internal
class FileStatusManagerImpl(private val project: Project, coroutineScope: CoroutineScope) : FileStatusManager(), Disposable {
  private val queue = MergingUpdateQueue.mergingUpdateQueue(
    name = "FileStatusManagerImpl",
    mergingTimeSpan = 100,
    coroutineScope = coroutineScope,
  )

  private val dirtyLock = Any()
  private val dirtyStatuses = HashSet<VirtualFile>()
  private val dirtyDocuments = Object2BooleanOpenHashMap<VirtualFile>()
  private val cachedStatuses = Collections.synchronizedMap(HashMap<VirtualFile, FileStatus>())
  private val whetherExactlyParentToChanged = Collections.synchronizedMap(HashMap<VirtualFile, Boolean?>())

  init {
    val connection = project.messageBus.connect(coroutineScope)
    connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { fileStatusesChanged() })
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsMappingListener(::fileStatusesChanged))
    connection.subscribe(ChangeListListener.TOPIC, MyChangeListListener())
    FileStatusProvider.EP_NAME.addChangeListener(project, ::fileStatusesChanged, project)
    StartupManager.getInstance(project).runAfterOpened(::fileStatusesChanged)
  }

  private inner class MyChangeListListener : ChangeListListener {
    override fun changeListAdded(list: ChangeList) {
      fileStatusesChanged()
    }

    override fun changeListRemoved(list: ChangeList) {
      fileStatusesChanged()
    }

    override fun changeListUpdateDone() {
      fileStatusesChanged()
    }
  }

  internal class FileStatusManagerDocumentListener : FileDocumentManagerListener, DocumentListener {
    private val CHANGED = Key.create<Boolean>("FileStatusManagerDocumentListener.document.changed")

    override fun documentChanged(event: DocumentEvent) {
      val document = event.document
      if (document.isInBulkUpdate) {
        document.putUserData(CHANGED, true)
      }
      else {
        refreshFileStatus(document)
      }
    }

    override fun bulkUpdateFinished(document: Document) {
      if (document.getUserData(CHANGED) != null) {
        document.putUserData(CHANGED, null)
        refreshFileStatus(document)
      }
    }

    override fun unsavedDocumentDropped(document: Document) {
      refreshFileStatus(document)
    }

    private fun refreshFileStatus(document: Document) {
      val file = FileDocumentManager.getInstance().getFile(document) ?: return
      if (!file.isInLocalFileSystem) return // no VCS
      if (!isSupported(file)) return

      val projectManager = ProjectManager.getInstanceIfCreated() ?: return
      for (project in projectManager.openProjects) {
        val manager = project.getServiceIfCreated(FileStatusManager::class.java) as FileStatusManagerImpl?
        manager?.refreshFileStatusFromDocument(file)
      }
    }
  }

  private fun calcStatus(virtualFile: VirtualFile): FileStatus {
    for (extension in FileStatusProvider.EP_NAME.getExtensions(project)) {
      val status = extension.getFileStatus(virtualFile)
      if (status != null) {
        LOG.debug { "File status for file [$virtualFile] from provider ${extension.javaClass.name}: $status" }
        return status
      }
    }

    if (virtualFile.isInLocalFileSystem) {
      val status = getVcsFileStatus(virtualFile)
      if (status != null) {
        LOG.debug { "File status for file [$virtualFile] from vcs provider: $status" }
        return status
      }
    }

    if (virtualFile.isValid && virtualFile.`is`(VFileProperty.SPECIAL)) {
      LOG.debug { "Default ignored status for special file [$virtualFile]" }
      return FileStatus.IGNORED
    }
    LOG.debug { "Default not_changed status for file [$virtualFile]" }
    return FileStatus.NOT_CHANGED
  }

  private fun getVcsFileStatus(virtualFile: VirtualFile): FileStatus? {
    if (ProjectLevelVcsManager.getInstance(project).getVcsFor(virtualFile) == null) {
      if (ScratchUtil.isScratch(virtualFile)) {
        // do not use for vcs-tracked scratched files
        return FileStatus.SUPPRESSED
      }
      else {
        return null
      }
    }

    val status = ChangeListManager.getInstance(project).getStatus(virtualFile)
    if (status != FileStatus.NOT_CHANGED) {
      return status
    }
    return if (isDocumentModified(virtualFile)) FileStatus.MODIFIED else null
  }

  private fun calcDirectoryStatus(virtualFile: VirtualFile, status: FileStatus): Boolean? {
    if (FileStatus.NOT_CHANGED != status || !VcsConfiguration.getInstance(project).SHOW_DIRTY_RECURSIVELY) {
      return null
    }

    val state = ChangeListManager.getInstance(project).haveChangesUnder(virtualFile)
    return when {
      // an immediate child is modified
      ThreeState.YES == state -> true
      // some child is modified
      ThreeState.UNSURE == state -> false
      // no modified files inside
      else -> null
    }
  }

  override fun dispose() {
    cachedStatuses.clear()
  }

  override fun addFileStatusListener(listener: FileStatusListener, parentDisposable: Disposable) {
    project.messageBus.connect(parentDisposable).subscribe(FileStatusListener.TOPIC, listener)
  }

  override fun fileStatusesChanged() {
    synchronized(dirtyLock) {
      cachedStatuses.clear()
      whetherExactlyParentToChanged.clear()
      dirtyStatuses.clear()
    }
    ApplicationManager.getApplication().invokeLater({ project.messageBus.syncPublisher(FileStatusListener.TOPIC).fileStatusesChanged() },
                                                    ModalityState.any(),
                                                    project.disposed)
  }

  override fun fileStatusChanged(file: VirtualFile?) {
    if (file == null) {
      return
    }

    if (!file.isValid) {
      synchronized(dirtyLock) {
        cachedStatuses.remove(file)
        whetherExactlyParentToChanged.remove(file)
      }
      return
    }

    if (!isSupported(file)) {
      // do not leak light files via cache
      return
    }
    if (cachedStatuses.get(file) == null) {
      return
    }

    synchronized(dirtyLock) { dirtyStatuses.add(file) }
    queue.queue(DisposableUpdate.createDisposable(this, "file status update", ::updateCachedFileStatuses))
  }

  @RequiresBackgroundThread
  private fun updateCachedFileStatuses() {
    var toRefresh: List<VirtualFile>
    synchronized(dirtyLock) {
      toRefresh = dirtyStatuses.toList()
      dirtyStatuses.clear()
    }
    val updatedFiles = ArrayList<VirtualFile>()
    for (file in toRefresh) {
      val wasUpdated = updateFileStatusFor(file)
      if (wasUpdated) {
        updatedFiles.add(file)
      }
    }
    ApplicationManager.getApplication().invokeLater(
      {
        val publisher = project.messageBus.syncPublisher(FileStatusListener.TOPIC)
        for (file in updatedFiles) {
          publisher.fileStatusChanged(file)
        }
      }, ModalityState.any(), project.disposed
    )
  }

  private fun updateFileStatusFor(file: VirtualFile): Boolean {
    val newStatus = calcStatus(file)
    val oldStatus = cachedStatuses.put(file, newStatus)
    if (oldStatus === newStatus) {
      return false
    }

    whetherExactlyParentToChanged.put(file, calcDirectoryStatus(file, newStatus))
    return true
  }

  private fun initFileStatusFor(file: VirtualFile): FileStatus {
    val newStatus = calcStatus(file)
    cachedStatuses.put(file, newStatus)
    whetherExactlyParentToChanged.put(file, calcDirectoryStatus(file, newStatus))
    return newStatus
  }

  override fun getStatus(file: VirtualFile): FileStatus {
    if (!isSupported(file)) {
      // do not leak light files via cache
      return FileStatus.SUPPRESSED
    }

    val status = cachedStatuses.get(file)
    LOG.debug { "Cached status for file [$file] is $status" }
    return status ?: initFileStatusFor(file)
  }

  override fun getRecursiveStatus(file: VirtualFile): FileStatus {
    val status = getStatus(file)
    if (status !== FileStatus.NOT_CHANGED) {
      return status
    }

    if (file.isValid && file.isDirectory) {
      val immediate = whetherExactlyParentToChanged.get(file) ?: return FileStatus.NOT_CHANGED
      return if (immediate) FileStatus.NOT_CHANGED_IMMEDIATE else FileStatus.NOT_CHANGED_RECURSIVE
    }
    return FileStatus.NOT_CHANGED
  }

  @RequiresEdt
  private fun refreshFileStatusFromDocument(file: VirtualFile) {
    if (LOG.isDebugEnabled) {
      val document = FileDocumentManager.getInstance().getDocument(file)
      LOG.debug("[refreshFileStatusFromDocument] file modificationStamp: ${file.modificationStamp}, " +
                "document modificationStamp: ${document?.modificationStamp}")
    }
    val isDocumentModified = isDocumentModified(file)
    synchronized(dirtyLock) { dirtyDocuments.put(file, isDocumentModified) }
    queue.queue(DisposableUpdate.createDisposable(this, "refresh from document", ::processModifiedDocuments))
  }

  @RequiresBackgroundThread
  private fun processModifiedDocuments() {
    var toRefresh: Object2BooleanMap<VirtualFile>
    synchronized(dirtyLock) {
      toRefresh = Object2BooleanOpenHashMap(dirtyDocuments)
      dirtyDocuments.clear()
    }
    toRefresh.forEach(::processModifiedDocument)
  }

  @RequiresBackgroundThread
  private fun processModifiedDocument(file: VirtualFile, isDocumentModified: Boolean) {
    if (LOG.isDebugEnabled) {
      val document = ApplicationManager.getApplication().runReadAction(Computable { FileDocumentManager.getInstance().getDocument(file) })
      LOG.debug(
        "[processModifiedDocument] isModified: ${isDocumentModified}, file modificationStamp: ${file.modificationStamp}, " +
        "document modificationStamp: ${document?.modificationStamp}"
      )
    }

    val vcsFile = VcsUtil.resolveSymlinkIfNeeded(project, file)

    val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(vcsFile) ?: return
    val cachedStatus = cachedStatuses[vcsFile]
    if (cachedStatus === FileStatus.MODIFIED && !isDocumentModified) {
      val unlockWithPrompt = (ReadonlyStatusHandler.getInstance(project) as ReadonlyStatusHandlerImpl).state.SHOW_DIALOG
      if (!unlockWithPrompt) {
        val rollbackEnvironment = vcs.rollbackEnvironment
        rollbackEnvironment?.rollbackIfUnchanged(vcsFile)
      }
    }

    if (cachedStatus != null) {
      val isStatusChanged = cachedStatus !== FileStatus.NOT_CHANGED
      if (isStatusChanged != isDocumentModified) {
        fileStatusChanged(vcsFile)
      }
    }

    val cp = vcs.changeProvider
    if (cp != null && cp.isModifiedDocumentTrackingRequired) {
      val status = ChangeListManager.getInstance(project).getStatus(vcsFile)
      val isClmStatusChanged = status !== FileStatus.NOT_CHANGED
      if (isClmStatusChanged != isDocumentModified) {
        VcsDirtyScopeManager.getInstance(project).fileDirty(vcsFile)
      }
    }
  }

  @TestOnly
  fun waitFor() {
    queue.waitForAllExecuted(10, TimeUnit.SECONDS)
    if (queue.isFlushing) {
      // MUQ.queue() inside Update.run cancels underlying future, and 'waitForAllExecuted' exits prematurely.
      // Workaround this issue by waiting twice
      // This fixes 'processModifiedDocument -> fileStatusChanged' interaction.
      queue.waitForAllExecuted(10, TimeUnit.SECONDS)
    }
  }
}

private fun isSupported(file: VirtualFile): Boolean {
  return FileDocumentManagerBase.isTrackable(file)
}

private fun isDocumentModified(virtualFile: VirtualFile): Boolean {
  return if (virtualFile.isDirectory) false else FileDocumentManager.getInstance().isFileModified(virtualFile)
}

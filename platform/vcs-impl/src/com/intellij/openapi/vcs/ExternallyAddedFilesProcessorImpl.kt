// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsConfiguration.StandardConfirmation.ADD
import com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY
import com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.VcsIgnoreManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import com.intellij.util.concurrency.QueueProcessor
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

internal const val ASKED_ADD_EXTERNAL_FILES_PROPERTY = "ASKED_ADD_EXTERNAL_FILES" //NON-NLS

private val LOG = logger<ExternallyAddedFilesProcessorImpl>()

/**
 * Extend [VcsVFSListener] to automatically add/propose to add into VCS files that were created not by IDE (externally created).
 */
internal class ExternallyAddedFilesProcessorImpl(
  project: Project,
  parentDisposable: Disposable,
  private val vcs: AbstractVcs,
  private val addChosenFiles: (Collection<VirtualFile>) -> Unit,
) : FilesProcessorWithNotificationImpl(project, parentDisposable), FilesProcessor, AsyncVfsEventsListener, ChangeListListener {
  private val UNPROCESSED_FILES_LOCK = ReentrantReadWriteLock()

  private val queue = QueueProcessor<Collection<VirtualFile>> { files -> processFiles(files) }

  private val unprocessedFiles = mutableSetOf<VirtualFile>()

  private val vcsManager = ProjectLevelVcsManager.getInstance(project)

  private val vcsIgnoreManager = VcsIgnoreManager.getInstance(project)

  fun install(coroutineScope: CoroutineScope) {
    project.messageBus.connect(coroutineScope).subscribe(ChangeListListener.TOPIC, this)
    AsyncVfsEventsPostProcessor.getInstance().addListener(this, coroutineScope)
  }

  override fun unchangedFileStatusChanged(upToDate: Boolean) {
    if (!upToDate) return
    if (!needProcessExternalFiles()) return

    val files: Set<VirtualFile>
    UNPROCESSED_FILES_LOCK.write {
      files = unprocessedFiles.toHashSet()
      unprocessedFiles.clear()
    }
    if (files.isEmpty()) return

    if (needDoForCurrentProject()) {
      LOG.debug("Add external files to ${vcs.displayName} silently ", files)
      addChosenFiles(doFilterFiles(files))
    }
    else if (Registry.`is`("vcs.show.externally.added.files.notification", false)) {
      LOG.debug("Process external files and prompt to add if needed to ${vcs.displayName} ", files)
      queue.add(files)
    }
  }

  override suspend fun filesChanged(events: List<VFileEvent>) {
    if (!needProcessExternalFiles()) {
      return
    }

    LOG.debug { "Got events $events" }

    if (events.isEmpty()) {
      return
    }

    val configDir = project.getProjectConfigDir()
    val externallyAddedFiles = events.asSequence()
      .filter { it.isFromRefresh && it is VFileCreateEvent && !isProjectConfigDirOrUnderIt(configDir, it.parent) }
      .mapNotNull(VFileEvent::getFile)
      .toList()

    if (externallyAddedFiles.isEmpty()) {
      return
    }

    LOG.debug { "Got external files from VFS events $externallyAddedFiles" }

    UNPROCESSED_FILES_LOCK.write {
      unprocessedFiles.addAll(externallyAddedFiles)
    }
  }

  private fun doNothingSilently() = vcsManager.getStandardConfirmation(ADD, vcs).value == DO_NOTHING_SILENTLY

  private fun needProcessExternalFiles(): Boolean {
    if (doNothingSilently()) return false
    if (!Registry.`is`("vcs.process.externally.added.files")) return false

    return true
  }

  override fun dispose() {
    super.dispose()
    queue.clear()
    UNPROCESSED_FILES_LOCK.write {
      unprocessedFiles.clear()
    }
  }

  override val notificationDisplayId: String = VcsNotificationIdsHolder.EXTERNALLY_ADDED_FILES
  override val askedBeforeProperty = ASKED_ADD_EXTERNAL_FILES_PROPERTY
  override val doForCurrentProjectProperty: String get() = throw UnsupportedOperationException() // usages overridden

  override val showActionText: String = VcsBundle.message("external.files.add.notification.action.view")
  override val forCurrentProjectActionText: String = VcsBundle.message("external.files.add.notification.action.add")
  override val muteActionText: String = VcsBundle.message("external.files.add.notification.action.mute")

  override val viewFilesDialogTitle: String = VcsBundle.message("external.files.add.view.dialog.title", vcs.displayName)

  override fun notificationTitle() = ""

  override fun notificationMessage(): String = VcsBundle.message("external.files.add.notification.message", vcs.displayName)

  override fun doActionOnChosenFiles(files: Collection<VirtualFile>) {
    addChosenFiles(files)
  }

  override fun setForCurrentProject(isEnabled: Boolean) {
    if (isEnabled) vcsManager.getStandardConfirmation(ADD, vcs).value = DO_ACTION_SILENTLY
    VcsConfiguration.getInstance(project).ADD_EXTERNAL_FILES_SILENTLY = isEnabled
  }

  override fun needDoForCurrentProject(): Boolean {
    return (vcsManager.getStandardConfirmation(ADD, vcs).value == DO_ACTION_SILENTLY
            && VcsConfiguration.getInstance(project).ADD_EXTERNAL_FILES_SILENTLY)
  }

  override fun doFilterFiles(files: Collection<VirtualFile>): Collection<VirtualFile> {
    val parents = files.toHashSet()
    return ChangeListManagerImpl.getInstanceImpl(project).unversionedFiles
      .asSequence()
      .filterNot(vcsIgnoreManager::isPotentiallyIgnoredFile)
      .filter { isUnder(parents, it) }
      .toSet()
  }

  private fun isUnder(parents: Set<VirtualFile>, child: VirtualFile) = generateSequence(child) { it.parent }.any { it in parents }

  private fun isProjectConfigDirOrUnderIt(configDir: VirtualFile?, file: VirtualFile): Boolean {
    return configDir != null && VfsUtilCore.isAncestor(configDir, file, false)
  }

  private fun Project.getProjectConfigDir(): VirtualFile? {
    if (!isDirectoryBased || isDefault) return null

    val projectConfigDir = stateStore.directoryStorePath?.let(LocalFileSystem.getInstance()::findFileByNioFile)
    if (projectConfigDir == null) {
      LOG.warn("Cannot find project config directory for non-default and non-directory based project ${name}")
    }
    return projectConfigDir
  }

  @TestOnly
  override fun waitForEventsProcessedInTestMode() {
    super.waitForEventsProcessedInTestMode()
    queue.waitFor()
  }
}

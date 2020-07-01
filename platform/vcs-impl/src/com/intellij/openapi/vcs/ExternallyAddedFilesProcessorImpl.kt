// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
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
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal const val ASKED_ADD_EXTERNAL_FILES_PROPERTY = "ASKED_ADD_EXTERNAL_FILES"

private val LOG = logger<ExternallyAddedFilesProcessorImpl>()

internal class ExternallyAddedFilesProcessorImpl(project: Project,
                                                 private val parentDisposable: Disposable,
                                                 private val vcs: AbstractVcs,
                                                 private val addChosenFiles: (Collection<VirtualFile>) -> Unit)
  : FilesProcessorWithNotificationImpl(project, parentDisposable), FilesProcessor, AsyncVfsEventsListener, ChangeListListener {

  private val UNPROCESSED_FILES_LOCK = ReentrantReadWriteLock()

  private val queue = QueueProcessor<List<VirtualFile>> { files -> processFiles(files) }

  private val unprocessedFiles = mutableSetOf<VirtualFile>()

  private val vcsManager = ProjectLevelVcsManager.getInstance(project)

  private val vcsIgnoreManager = VcsIgnoreManager.getInstance(project)

  fun install() {
    runReadAction {
      if (!project.isDisposed) {
        project.messageBus.connect(parentDisposable).subscribe(ChangeListListener.TOPIC, this)
        AsyncVfsEventsPostProcessor.getInstance().addListener(this, this)
      }
    }
  }

  override fun changeListUpdateDone() {
    if (!needProcessExternalFiles()) return

    val files = UNPROCESSED_FILES_LOCK.read { unprocessedFiles.toList() }

    UNPROCESSED_FILES_LOCK.write {
      unprocessedFiles.removeAll(files)
    }

    if (files.isEmpty()) return

    if (needDoForCurrentProject()) {
      LOG.debug("Add external files to ${vcs.displayName} silently ", files)
      addChosenFiles(doFilterFiles(files))
    }
    else {
      LOG.debug("Process external files and prompt to add if needed to ${vcs.displayName} ", files)
      queue.add(files)
    }
  }

  override fun filesChanged(events: List<VFileEvent>) {
    if (!needProcessExternalFiles()) return

    LOG.debug("Got events", events)
    val configDir = project.getProjectConfigDir()
    val externallyAddedFiles =
      events.asSequence()
        .filter {
          it.isFromRefresh &&
          it is VFileCreateEvent &&
          !isProjectConfigDirOrUnderIt(configDir, it.parent)
        }
        .mapNotNull(VFileEvent::getFile)
        .toSet()

    if (externallyAddedFiles.isEmpty()) return
    LOG.debug("Got external files from VFS events", externallyAddedFiles)

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

  override val notificationDisplayId: String = "externally.added.files.notification"
  override val askedBeforeProperty = ASKED_ADD_EXTERNAL_FILES_PROPERTY
  override val doForCurrentProjectProperty: String? = null

  override val showActionText: String = VcsBundle.message("external.files.add.notification.action.view")
  override val forCurrentProjectActionText: String = VcsBundle.message("external.files.add.notification.action.add")
  override val forAllProjectsActionText: String? = null
  override val muteActionText: String = VcsBundle.message("external.files.add.notification.action.mute")

  override val viewFilesDialogTitle: String? = VcsBundle.message("external.files.add.view.dialog.title", vcs.displayName)

  override fun notificationTitle() = ""

  override fun notificationMessage(): String = VcsBundle.message("external.files.add.notification.message", vcs.displayName)

  override fun doActionOnChosenFiles(files: Collection<VirtualFile>) {
    addChosenFiles(files)
  }

  override fun rememberForCurrentProject() {
    vcsManager.getStandardConfirmation(ADD, vcs).value = DO_ACTION_SILENTLY
    VcsConfiguration.getInstance(project).ADD_EXTERNAL_FILES_SILENTLY = true
  }

  override fun needDoForCurrentProject() =
    vcsManager.getStandardConfirmation(ADD, vcs).value == DO_ACTION_SILENTLY
    && VcsConfiguration.getInstance(project).ADD_EXTERNAL_FILES_SILENTLY

  override fun doFilterFiles(files: Collection<VirtualFile>): Collection<VirtualFile> =
    ChangeListManagerImpl.getInstanceImpl(project).unversionedFiles
      .asSequence()
      .filterNot(vcsIgnoreManager::isPotentiallyIgnoredFile)
      .filter { isUnder(files, it) }
      .toSet()

  override fun rememberForAllProjects() {}

  private fun isUnder(parents: Collection<VirtualFile>, child: VirtualFile) = generateSequence(child) { it.parent }.any { it in parents }

  private fun isProjectConfigDirOrUnderIt(configDir: VirtualFile?, file: VirtualFile) =
    configDir != null && VfsUtilCore.isAncestor(configDir, file, false)

  private fun Project.getProjectConfigDir(): VirtualFile? {
    if (!isDirectoryBased || isDefault) return null

    val projectConfigDir = stateStore.projectConfigDir?.let(LocalFileSystem.getInstance()::findFileByNioFile)
    if (projectConfigDir == null) {
      LOG.warn("Cannot find project config directory for non-default and non-directory based project ${name}")
    }
    return projectConfigDir
  }

  @TestOnly
  fun waitForEventsProcessedInTestMode() {
    assert(ApplicationManager.getApplication().isUnitTestMode)
    queue.waitFor()
  }
}
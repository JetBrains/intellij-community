// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.QueueProcessor
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private const val ADD_EXTERNAL_FILES_PROPERTY = "ADD_EXTERNAL_FILES"
private const val ASKED_ADD_EXTERNAL_FILES_PROPERTY = "ASKED_ADD_EXTERNAL_FILES"

private val LOG = logger<ExternallyAddedFilesProcessorImpl>()

class ExternallyAddedFilesProcessorImpl(project: Project,
                                        parentDisposable: Disposable,
                                        private val vcs: AbstractVcs<*>,
                                        private val addChosenFiles: (Collection<VirtualFile>) -> Unit)
  : FilesProcessorWithNotificationImpl(project, parentDisposable), FilesProcessor, AsyncVfsEventsListener, ChangeListListener {

  private val UNPROCESSED_FILES_LOCK = ReentrantReadWriteLock()

  private val queue = QueueProcessor<List<VirtualFile>> { files -> processFiles(files) }

  private val unprocessedFiles = mutableSetOf<VirtualFile>()

  private val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)

  private val vcsManager = ProjectLevelVcsManager.getInstance(project)

  init {
    runReadAction {
      if (!project.isDisposed) {
        changeListManager.addChangeListListener(this, parentDisposable)
        AsyncVfsEventsPostProcessor.getInstance().addListener(this, this)
      }
    }
  }

  override fun changeListUpdateDone() {
    if (!needProcessExternalFiles()) return

    val files = UNPROCESSED_FILES_LOCK.read { unprocessedFiles.toList() }
    if (files.isEmpty()) return

    if (needAddSilently()) {
      LOG.debug("Add external files to ${vcs.displayName} silently ", files)
      addChosenFiles(doFilterFiles(files))
    }
    else {
      LOG.debug("Process external files and prompt to add if needed to ${vcs.displayName} ", files)
      queue.add(files)
    }

    UNPROCESSED_FILES_LOCK.write {
      unprocessedFiles.clear()
    }
  }

  override fun filesChanged(events: List<VFileEvent>) {
    if (!needProcessExternalFiles()) return

    val externallyAddedFiles =
      events.asSequence()
        .filter(VFileEvent::isFromRefresh)
        .filter { it is VFileCreateEvent }
        .mapNotNull(VFileEvent::getFile)
        .toSet()

    if (externallyAddedFiles.isEmpty()) return
    LOG.debug("Got external files from VFS events", externallyAddedFiles)

    UNPROCESSED_FILES_LOCK.write {
      unprocessedFiles.addAll(externallyAddedFiles)
    }
  }

  private fun needAddSilently() =
    vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD,
                                       vcs).value == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY
    && VcsConfiguration.getInstance(project).ADD_EXTERNAL_FILES_SILENTLY

  private fun doNothingSilently() = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD,
                                                                       vcs).value == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY

  private fun needProcessExternalFiles(): Boolean {
    if (doNothingSilently()) return false
    if (!Registry.`is`("vcs.process.externally.added.files")) return false

    return true
  }

  override fun dispose() {
    super.dispose()
    queue.clear()
    unprocessedFiles.clear()
  }

  override val askedBeforeProperty = ASKED_ADD_EXTERNAL_FILES_PROPERTY
  override val doForCurrentProjectProperty = ADD_EXTERNAL_FILES_PROPERTY

  override val showActionText: String = VcsBundle.message("external.files.add.notification.action.view")
  override val forCurrentProjectActionText: String = VcsBundle.message("external.files.add.notification.action.add")
  override val forAllProjectsActionText: String? = null
  override val muteActionText: String = VcsBundle.message("external.files.add.notification.action.mute")

  override fun notificationTitle() = ""

  override fun notificationMessage(): String = VcsBundle.message("external.files.add.notification.message", vcs.displayName)

  override fun doActionOnChosenFiles(files: Collection<VirtualFile>) {
    addChosenFiles(files)
  }

  override fun doFilterFiles(files: Collection<VirtualFile>): Collection<VirtualFile> =
    changeListManager.unversionedFiles.filter { isUnder(files, it) }

  override fun rememberForAllProjects() {}

  private fun isUnder(parents: Collection<VirtualFile>, child: VirtualFile) = generateSequence(child) { it.parent }.any { it in parents }

  @TestOnly
  fun waitForEventsProcessedInTestMode() {
    assert(ApplicationManager.getApplication().isUnitTestMode)
    queue.waitFor()
  }
}
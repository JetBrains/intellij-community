// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
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
import com.intellij.project.stateStore
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

private val LOG = logger<ExternallyAddedFilesProcessorImpl>()

/**
 * Extend [VcsVFSListener] to automatically add/propose to add into VCS files that were created not by IDE (externally created).
 */
internal class ExternallyAddedFilesProcessorImpl(
  private val project: Project,
  parentDisposable: Disposable,
  private val vcs: AbstractVcs,
  private val addChosenFiles: (Collection<VirtualFile>) -> Unit,
) : AsyncVfsEventsListener, ChangeListListener, Disposable {
  private val UNPROCESSED_FILES_LOCK = ReentrantReadWriteLock()

  private val unprocessedFiles = mutableSetOf<VirtualFile>()

  private val vcsManager = ProjectLevelVcsManager.getInstance(project)

  private val vcsIgnoreManager = VcsIgnoreManager.getInstance(project)

  init {
    Disposer.register(parentDisposable, this)
  }

  fun install(coroutineScope: CoroutineScope) {
    project.messageBus.connect(coroutineScope).subscribe(ChangeListListener.TOPIC, this)
    AsyncVfsEventsPostProcessor.getInstance().addListener(this, coroutineScope)
  }

  override fun unchangedFileStatusChanged(upToDate: Boolean) {
    if (!upToDate) return
    if (doNothingSilently()) return

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
  }

  override suspend fun filesChanged(events: List<VFileEvent>) {
    if (doNothingSilently()) {
      return
    }

    LOG.debug { "Got events $events" }

    if (events.isEmpty()) {
      return
    }

    val configDir = getProjectConfigDir(project)
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

  private fun doNothingSilently() =
    Registry.`is`("vcs.files.processing.do.nothing", false) || vcsManager.getStandardConfirmation(ADD, vcs).value == DO_NOTHING_SILENTLY

  override fun dispose() {
    UNPROCESSED_FILES_LOCK.write {
      unprocessedFiles.clear()
    }
  }

  private fun needDoForCurrentProject(): Boolean {
    return (vcsManager.getStandardConfirmation(ADD, vcs).value == DO_ACTION_SILENTLY
            && VcsConfiguration.getInstance(project).ADD_EXTERNAL_FILES_SILENTLY)
  }

  private fun doFilterFiles(files: Collection<VirtualFile>): Collection<VirtualFile> {
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

  private fun getProjectConfigDir(project: Project): VirtualFile? {
    val directoryStorePath = (if (project.isDefault) null else project.stateStore.directoryStorePath) ?: return null
    val projectConfigDir = LocalFileSystem.getInstance().findFileByNioFile(directoryStorePath)
    if (projectConfigDir == null) {
      LOG.warn("Cannot find project config directory for non-default and non-directory based project ${project.name}")
    }
    return projectConfigDir
  }
}

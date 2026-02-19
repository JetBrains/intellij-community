// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl.Companion.waitEventsProcessed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.coroutines.coroutineContext

/**
 * Listens to file system events and notifies VcsDirtyScopeManagers responsible for changed files to mark these files dirty.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class VcsDirtyScopeVfsListener(private val project: Project, coroutineScope: CoroutineScope) : AsyncVfsEventsListener {
  // for tests only
  private var isForbid = false

  init {
    AsyncVfsEventsPostProcessor.getInstance().addListener(this, coroutineScope)
  }

  @TestOnly
  fun setForbid(forbid: Boolean) {
    isForbid = forbid
  }

  @TestOnly
  fun waitForAsyncTaskCompletion() {
    thisLogger().debug("waitForAsyncTaskCompletion")
    waitEventsProcessed()
  }

  override suspend fun filesChanged(events: List<VFileEvent>) {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    if (isForbid || !vcsManager.hasActiveVcss()) {
      return
    }

    val dirtyFilesAndDirs = FilesAndDirs()
    // collect files and directories - sources of events
    for (event in events) {
      coroutineContext.job.ensureActive()

      val isDirectory: Boolean
      if (event is VFileCreateEvent) {
        if (!event.parent.isInLocalFileSystem) {
          continue
        }
        isDirectory = event.isDirectory
      }
      else {
        val file = requireNotNull(event.file) { "All events but VFileCreateEvent have @NotNull getFile()" }
        if (!file.isInLocalFileSystem) {
          continue
        }
        isDirectory = file.isDirectory
      }

      if (event is VFileMoveEvent) {
        add(vcsManager, dirtyFilesAndDirs, VcsUtil.getFilePath(event.oldPath, isDirectory))
        add(vcsManager, dirtyFilesAndDirs, VcsUtil.getFilePath(event.newPath, isDirectory))
      }
      else if (event is VFilePropertyChangeEvent && event.isRename) {
        // if a file was renamed, then the file is dirty, and its parent directory is dirty too;
        // if a directory was renamed, all its children are recursively dirty, the parent dir is also dirty but not recursive.
        val oldPath = VcsUtil.getFilePath(event.oldPath, isDirectory)
        val newPath = VcsUtil.getFilePath(event.newPath, isDirectory)
        // the file is dirty recursively, its old directory is dirty alone
        addWithParentDirectory(vcsManager, dirtyFilesAndDirs, oldPath)
        add(vcsManager, dirtyFilesAndDirs, newPath)
      }
      else {
        add(vcsManager, dirtyFilesAndDirs, VcsUtil.getFilePath(event.path, isDirectory))
      }
    }

    val dirtyScopeManager = VcsDirtyScopeManagerImpl.getInstanceImpl(project)
    dirtyScopeManager.fileVcsPathsDirty(dirtyFilesAndDirs.files.asMap(), dirtyFilesAndDirs.dirs.asMap())
  }
}

/**
 * Stores VcsDirtyScopeManagers and files and directories, which should be marked dirty by them.
 * Files will be marked dirty, directories will be marked recursively dirty, so if you need to mark dirty a directory, but
 * not recursively, you should add it to files.
 */
private class FilesAndDirs {
  @JvmField var files: VcsDirtyScopeMap = VcsDirtyScopeMap()
  @JvmField var dirs: VcsDirtyScopeMap = VcsDirtyScopeMap()
}

private fun add(
  vcsManager: ProjectLevelVcsManager,
  filesAndDirs: FilesAndDirs,
  filePath: FilePath,
  withParentDirectory: Boolean = false
) {
  val vcsRoot = vcsManager.getVcsRootObjectFor(filePath)
  val vcs = vcsRoot?.vcs
  if (vcsRoot == null || vcs == null) return

  if (filePath.isDirectory) {
    filesAndDirs.dirs.add(vcsRoot, filePath)
  }
  else {
    filesAndDirs.files.add(vcsRoot, filePath)
  }

  if (withParentDirectory && vcs.areDirectoriesVersionedItems()) {
    val parentPath = filePath.parentPath
    if (parentPath != null && vcsManager.getVcsFor(parentPath) === vcs) {
      filesAndDirs.files.add(vcsRoot, parentPath)
    }
  }
}

private fun addWithParentDirectory(
  vcsManager: ProjectLevelVcsManager,
  filesAndDirs: FilesAndDirs,
  filePath: FilePath
) {
  add(vcsManager, filesAndDirs, filePath, true)
}

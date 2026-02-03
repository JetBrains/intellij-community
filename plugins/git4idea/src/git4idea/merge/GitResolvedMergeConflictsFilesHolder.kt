// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.merge

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.VcsIgnoreManagerImpl
import com.intellij.openapi.vcs.changes.VcsManagedFilesHolder
import com.intellij.openapi.vcs.merge.MergeConflictManager
import com.intellij.openapi.vcs.util.paths.RecursiveFilePathSet
import com.intellij.util.ui.update.ComparableObject
import com.intellij.util.ui.update.DisposableUpdate
import git4idea.changes.GitChangeUtils
import git4idea.repo.CopyOnWriteFilePathSet
import git4idea.repo.GitRepository
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@ApiStatus.Internal
class GitResolvedMergeConflictsFilesHolder(private val repository: GitRepository): Disposable {

  private val LOCK = ReentrantReadWriteLock()

  private var inUpdate = false

  private val resolvedFiles: CopyOnWriteFilePathSet = CopyOnWriteFilePathSet(repository.root.isCaseSensitive)

  private val queue = VcsIgnoreManagerImpl.getInstanceImpl(repository.project).ignoreRefreshQueue

  init {
    scheduleUpdate()
  }

  fun resolvedConflictsFilePaths(): Collection<FilePath> = resolvedFiles.toSet()

  fun isInUpdateMode(): Boolean = LOCK.read { inUpdate }

  fun containsResolvedFile(filePath: FilePath): Boolean = resolvedFiles.hasAncestor(filePath)

  fun invalidate() {
    scheduleUpdate()
  }

  private fun scheduleUpdate() {
    val project = repository.project
    if (!MergeConflictManager.isNonModalMergeEnabled(project)) {
      clear()
      return
    }

    LOCK.write {
      inUpdate = true
    }
    BackgroundTaskUtil.syncPublisher(project, VcsManagedFilesHolder.TOPIC).updatingModeChanged()
    queue.queue(DisposableUpdate.createDisposable(this,
                                                  ComparableObject.Impl(this, "update resolved conflicts"),
                                                  Runnable { this.update() }))
  }

  private fun update() {
    if (repository.state == Repository.State.NORMAL) {
      clear()
      notifyListeners()
      return
    }
    val actualResolvedFiles = GitChangeUtils.getResolvedFiles(repository)
    if (actualResolvedFiles.isEmpty()) {
      clear()
      notifyListeners()
      return
    }
    val actualResolvedFilesSet = RecursiveFilePathSet(repository.root.isCaseSensitive).apply { addAll(actualResolvedFiles) }

    LOCK.write {
      resolvedFiles.set(actualResolvedFilesSet)

      inUpdate = false
    }

    notifyListeners()
  }

  private fun notifyListeners() {
    val project = repository.project
    ChangeListManagerImpl.getInstanceImpl(project).notifyUnchangedFileStatusChanged()
    BackgroundTaskUtil.syncPublisher(project, VcsManagedFilesHolder.TOPIC).updatingModeChanged()
  }

  override fun dispose() {
    clear()
  }

  private fun clear() {
    LOCK.write {
      if (resolvedFiles.initialized) {
        resolvedFiles.clear()
      }
    }
  }
}

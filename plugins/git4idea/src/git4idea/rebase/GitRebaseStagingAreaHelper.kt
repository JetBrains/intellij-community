// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.withProgressText
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.GitDisposable
import git4idea.GitUtil
import git4idea.i18n.GitBundle
import git4idea.index.GitFileStatus
import git4idea.repo.GitRepository
import git4idea.util.GitFileUtils
import git4idea.util.gitFreezingProcess
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal object GitRebaseStagingAreaHelper {
  @JvmStatic
  fun tryStageChangesInTrackedFilesAndRetryInBackground(repository: GitRepository, showError: () -> Unit): Job {
    val project = repository.project
    return GitDisposable.getInstance(project).coroutineScope.launch {
      withBackgroundProgress(project, GitBundle.message("rebase.progress.indicator.continue.title")) {
        gitFreezingProcess(project, GitBundle.message("rebase.git.operation.name")) {
          LOG.info("Staging changes and re-trying rebase")
          val staged = withProgressText(GitBundle.message("stage.add.process")) {
            tryStageChangesInTrackedFiles(repository)
          }
          if (staged) {
            LOG.info("Changes in tracked files were staged")
            coroutineToIndicator {
              GitRebaseUtils.continueRebaseWithoutFreezing(project)
            }
          }
          else {
            showError()
          }
        }
      }
    }
  }

  /**
   * Makes an attempt to stage changes in tracked files only if these changes affect already staged files.
   * E.g., a file was modified in the editor after a conflict resolution, but git add wasn't performed.
   *
   * @return true if the staging area was successfully updated
   */
  @JvmStatic
  @RequiresBackgroundThread
  fun tryStagePartiallyStaged(repository: GitRepository): Boolean {
    val stagingAreaHolder = repository.stagingAreaHolder
    val update = StagingAreaUpdate()
    stagingAreaHolder.allRecords.forEach { record ->
      if (record.isConflicted()) return false
      if (!record.isUntracked() && !record.isIgnored()) {
        if (record.getStagedStatus() == null) {
          LOG.debug("Unstaged file detected ${record.path}")
          return false
        }

        if (!update.tryAppend(record)) return false
      }
    }
    if (update.empty) return false

    ProgressManager.checkCanceled()

    return update.apply(repository)
  }

  @JvmStatic
  fun showUnstagedTrackedFilesDialog(repository: GitRepository) {
    val paths = repository.stagingAreaHolder.allRecords.mapNotNull { fileStatus ->
      if (fileStatus.getUnStagedStatus() != null) fileStatus.path.path else null
    }

    GitUtil.showPathsInDialog(
      repository.project,
      paths,
      GitBundle.message("rebase.tracked.files.with.unstaged.changes.title"),
      null,
      GitBundle.message("rebase.tracked.files.with.unstaged.changes.empty.text")
    )
  }

  private suspend fun tryStageChangesInTrackedFiles(repository: GitRepository): Boolean {
    val stagingAreaHolder = repository.stagingAreaHolder

    val update = StagingAreaUpdate()
    stagingAreaHolder.allRecords.forEach { record ->
      if (record.isConflicted()) return false
      if (record.isTracked()) {
        if (!update.tryAppend(record)) return false
      }
    }

    checkCanceled()

    return update.apply(repository)
  }

  private class StagingAreaUpdate {
    private val toAdd = mutableListOf<FilePath>()
    private val toRemove = mutableListOf<FilePath>()

    val empty: Boolean get() = toAdd.isEmpty() && toRemove.isEmpty()

    fun tryAppend(record: GitFileStatus): Boolean {
      val unStagedStatus = record.getUnStagedStatus()

      if (unStagedStatus != null) {
        when (unStagedStatus) {
          FileStatus.MODIFIED -> toAdd.add(record.path)
          FileStatus.DELETED -> toRemove.add(record.path)
          else -> {
            LOG.debug("${record.path} has status ${record.getUnStagedStatus()}")
            return false
          }
        }
      }

      return true
    }

    fun apply(repository: GitRepository): Boolean = try {
      if (toAdd.isNotEmpty()) {
        LOG.debug("Adding ${toAdd.size} files")
        GitFileUtils.addPaths(repository.project, repository.root, toAdd)
      }
      if (toRemove.isNotEmpty()) {
        LOG.debug("Deleting ${toRemove.size} files")
        GitFileUtils.deletePaths(repository.project, repository.root, toRemove)
      }

      LOG.debug("Staging area was updated")

      true
    }
    catch (e: VcsException) {
      LOG.error("Couldn't stage unstaged changes", e)
      false
    }
  }

  private val LOG = thisLogger()
}

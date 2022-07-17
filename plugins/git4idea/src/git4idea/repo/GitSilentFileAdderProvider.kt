// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo

import com.intellij.openapi.GitSilentFileAdder
import com.intellij.openapi.GitSilentFileAdderProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsFileListenerContextHelper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.util.GitFileUtils
import java.io.File
import java.nio.file.Path

class GitSilentFileAdderProviderImpl(private val project: Project) : GitSilentFileAdderProvider {
  override fun create(): GitSilentFileAdder = GitSilentFileAdderImpl(project)
}

class GitSilentFileAdderImpl(private val project: Project) : GitSilentFileAdder {
  private val gitVcs = GitVcs.getInstance(project)
  private val vcsManager = ProjectLevelVcsManager.getInstance(project)
  private val vcsFileListenerContextHelper = VcsFileListenerContextHelper.getInstance(project)

  private val pendingAddition: MutableSet<FilePath> = HashSet()

  override fun markFileForAdding(path: String, isDirectory: Boolean) {
    addFile(VcsUtil.getFilePath(path, isDirectory))
  }

  override fun markFileForAdding(file: VirtualFile) {
    if (file.isInLocalFileSystem) {
      addFile(VcsUtil.getFilePath(file))
    }
  }

  override fun markFileForAdding(file: File, isDirectory: Boolean) {
    addFile(VcsUtil.getFilePath(file, isDirectory))
  }

  override fun markFileForAdding(path: Path, isDirectory: Boolean) {
    addFile(VcsUtil.getFilePath(path, isDirectory))
  }

  private fun addFile(filePath: FilePath) {
    val vcsRoot = vcsManager.getVcsRootObjectFor(filePath)
    if (vcsRoot == null || vcsRoot.vcs != gitVcs) return

    if (filePath.isDirectory) {
      vcsFileListenerContextHelper.ignoreAddedRecursive(listOf(filePath))
    }
    else {
      vcsFileListenerContextHelper.ignoreAdded(listOf(filePath))
    }

    pendingAddition.add(filePath)
  }

  override fun finish() {
    vcsFileListenerContextHelper.clearContext()

    val filesToAdd = pendingAddition.toList()
    pendingAddition.clear()

    @Suppress("IncorrectParentDisposable")
    BackgroundTaskUtil.executeOnPooledThread(project) { addToVcs(filesToAdd) }
  }

  /**
   * Called on pooled thread when all operations are completed.
   */
  private fun addToVcs(filePaths: Collection<FilePath>) {
    val map = GitUtil.sortFilePathsByGitRoot(project, filePaths)
    for ((root, rootPaths) in map) {
      try {
        GitFileUtils.addPaths(project, root, rootPaths)
      }
      catch (e: VcsException) {
        logger<GitSilentFileAdderImpl>().warn(e)
      }
    }
  }
}
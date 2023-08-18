// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo

import com.intellij.dvcs.repo.VcsManagedFilesHolderBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.VcsManagedFilesHolder
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitVcs

class GitUnversionedFilesHolder private constructor(val manager: GitRepositoryManager) : VcsManagedFilesHolderBase() {
  private val allHolders get() = manager.repositories.asSequence().map { it.untrackedFilesHolder }

  override fun isInUpdatingMode() = allHolders.any(GitUntrackedFilesHolder::isInUpdateMode)

  override fun containsFile(file: FilePath, vcsRoot: VirtualFile): Boolean {
    val repository = manager.getRepositoryForRootQuick(vcsRoot) ?: return false
    return repository.untrackedFilesHolder.containsUntrackedFile(file)
  }

  override fun values() = allHolders.flatMap { it.untrackedFilePaths }.toList()

  class Provider(project: Project) : VcsManagedFilesHolder.Provider {
    private val gitVcs = GitVcs.getInstance(project)
    private val manager = GitRepositoryManager.getInstance(project)

    override fun getVcs() = gitVcs

    override fun createHolder() = GitUnversionedFilesHolder(manager)
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.merge

import com.intellij.dvcs.repo.VcsManagedFilesHolderBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.VcsDirtyScope
import com.intellij.openapi.vcs.changes.VcsManagedFilesHolder
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitVcs
import git4idea.repo.GitRepositoryManager

internal class GitResolvedConflictsFilesHolder private constructor(val manager: GitRepositoryManager) : VcsManagedFilesHolderBase() {
  private val allHolders get() = manager.repositories.asSequence().map { it.resolvedConflictsFilesHolder }

  override fun isInUpdatingMode(): Boolean = allHolders.any(GitResolvedMergeConflictsFilesHolder::isInUpdateMode)

  override fun containsFile(file: FilePath, vcsRoot: VirtualFile): Boolean {
    val repository = manager.getRepositoryForRootQuick(vcsRoot) ?: return false
    return repository.resolvedConflictsFilesHolder.containsResolvedFile(file)
  }

  override fun values(): List<FilePath> = allHolders.flatMap { it.resolvedConflictsFilePaths() }.toList()

  override fun cleanUnderScope(scope: VcsDirtyScope) {
    for (holder in allHolders) {
      holder.invalidate()
    }
  }

  override fun cleanAll() {
    for (holder in allHolders) {
      holder.invalidate()
    }
  }

  internal class Provider(project: Project) : VcsManagedFilesHolder.Provider {
    private val gitVcs = GitVcs.getInstance(project)
    private val manager = GitRepositoryManager.getInstance(project)

    override fun getVcs(): GitVcs = gitVcs

    override fun createHolder(): GitResolvedConflictsFilesHolder = GitResolvedConflictsFilesHolder(manager)
  }
}

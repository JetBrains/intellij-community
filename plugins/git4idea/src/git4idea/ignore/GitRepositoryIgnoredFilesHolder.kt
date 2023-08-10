// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.openapi.vcs.FilePath
import git4idea.repo.GitRepository
import git4idea.repo.GitUntrackedFilesHolder
import org.jetbrains.annotations.TestOnly

class GitRepositoryIgnoredFilesHolder(private val repository: GitRepository) {
  fun isInUpdateMode(): Boolean = repository.untrackedFilesHolder.isInUpdateMode
  fun containsFile(file: FilePath): Boolean = repository.untrackedFilesHolder.containsIgnoredFile(file)
  val ignoredFilePaths: Set<FilePath> get() = repository.untrackedFilesHolder.ignoredFilePaths.toSet()
  fun retrieveIgnoredFilePaths(): Collection<FilePath> = repository.untrackedFilesHolder.retrieveIgnoredFilePaths()

  fun removeIgnoredFiles(filePaths: Collection<FilePath>) {
    repository.untrackedFilesHolder.removeIgnored(filePaths)
  }

  @TestOnly
  fun createWaiter(): GitUntrackedFilesHolder.Waiter = repository.untrackedFilesHolder.createWaiter()
}
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.openapi.vcs.FilePath
import git4idea.repo.GitRepository
import git4idea.repo.GitUntrackedFilesHolder
import org.jetbrains.annotations.TestOnly

interface GitRepositoryIgnoredFilesHolder {
  val ignoredFilePaths: Set<FilePath>

  val initialized: Boolean

  fun isInUpdateMode(): Boolean

  fun containsFile(file: FilePath): Boolean

  fun removeIgnoredFiles(filePaths: Collection<FilePath>)
}
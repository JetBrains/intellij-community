// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.openapi.vcs.FilePath

abstract class GitRepositoryIgnoredFilesHolder {
  abstract val ignoredFilePaths: Set<FilePath>

  abstract val initialized: Boolean

  abstract fun isInUpdateMode(): Boolean

  abstract fun containsFile(file: FilePath): Boolean

  abstract fun removeIgnoredFiles(filePaths: Collection<FilePath>)
}
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.IgnoredFileContentProvider
import com.intellij.openapi.vcs.changes.IgnoredFileProvider
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitVcs
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import java.lang.System.lineSeparator

open class GitIgnoredFileContentProvider(private val project: Project) : IgnoredFileContentProvider {

  override fun getSupportedVcs(): VcsKey = GitVcs.getKey()

  override fun getFileName() = GITIGNORE

  override fun buildIgnoreFileContent(ignoreFileRoot: VirtualFile, ignoredFileProviders: Array<IgnoredFileProvider>): String {
    val content = StringBuilder()
    for (i in ignoredFileProviders.indices) {
      val provider = ignoredFileProviders[i]
      val ignoredFileMasks = provider.getIgnoredFilesMasks(project, ignoreFileRoot)
      if (ignoredFileMasks.isEmpty()) continue

      val description = provider.masksGroupDescription
      if (description.isNotBlank()) {
        content.append(prependCommentHashCharacterIfNeeded(description))
        content.append(lineSeparator())
      }
      content.append(ignoredFileMasks.joinToString(lineSeparator()))

      if (i + 1 < ignoredFileProviders.size) {
        content.append(lineSeparator()).append(lineSeparator())
      }
    }
    return content.trimEnd { it == '\r' || it == '\n' }.toString()
  }

  private fun prependCommentHashCharacterIfNeeded(description: String): String =
    if (description.startsWith("#")) description else "# $description"
}

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.intellij.navigation.JBProtocolNavigateCommand
import com.intellij.navigation.JBProtocolRevisionResolver
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.vfs.VcsFileSystem
import com.intellij.openapi.vcs.vfs.VcsVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil

private val LOG = logger<JBProtocolNavigateCommand>()


class GitNavigateRevisionResolver: JBProtocolRevisionResolver {

  override fun resolve(project: Project, absolutePath: String, revision: String): VirtualFile? {
    val filePath = VcsUtil.getFilePath(absolutePath)
    try {
      val root = GitUtil.getRootForFile(project, filePath)
      val revisionNumber = GitRevisionNumber.resolve(project, root, revision)
      return VcsVirtualFile(absolutePath, GitFileRevision(project, filePath, revisionNumber), VcsFileSystem.getInstance())
    } catch (e: VcsException) {
      LOG.warn("Revison $revision can't be found", e)
      return null
    }
  }
}
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.intellij.navigation.JBProtocolRevisionResolver
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.vfs.VcsFileSystem
import com.intellij.openapi.vcs.vfs.VcsVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil

class GitNavigateRevisionResolver: JBProtocolRevisionResolver {

  override fun resolve(project: Project, absolutePath: String, revision: String): VirtualFile? {
    val filePath = VcsUtil.getFilePath(absolutePath)
    try {
      val root = GitUtil.getRootForFile(project, filePath)
      val revisionNumber = GitRevisionNumber.resolve(project, root, revision)
      val fileRevision = GitFileRevision(project, filePath, revisionNumber).also { it.loadContent() }
      return VcsVirtualFile(absolutePath, fileRevision, VcsFileSystem.getInstance())
    } catch (e: VcsException) {
      thisLogger().info("File $absolutePath can't be found in revision $revision", e)
      return null
    }
  }
}
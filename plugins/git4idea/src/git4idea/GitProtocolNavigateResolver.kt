// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.intellij.navigation.JBProtocolNavigateResolver
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.vfs.VcsFileSystem
import com.intellij.openapi.vcs.vfs.VcsVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil

private const val REVISON = "revision"

class GitProtocolNavigateResolver: JBProtocolNavigateResolver {

  override fun resolve(absolutePath: String, project: Project, parameters: MutableMap<String, String>): VirtualFile? {
    val revision = parameters[REVISON] ?: return null

    val filePath = VcsUtil.getFilePath(absolutePath)
    try {
      val root = GitUtil.getRootForFile(project, filePath)
      val revisionNumber = GitRevisionNumber.resolve(project, root, revision)
      return VcsVirtualFile(absolutePath, GitFileRevision(project, filePath, revisionNumber), VcsFileSystem.getInstance())
    } catch (ignored: VcsException) {
      return null
    }
  }
}
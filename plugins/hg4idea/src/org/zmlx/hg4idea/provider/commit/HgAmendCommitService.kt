// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.provider.commit

import com.intellij.dvcs.commit.AmendCommitService
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.zmlx.hg4idea.HgVcs
import org.zmlx.hg4idea.execution.HgCommandExecutor

@Service(Service.Level.PROJECT)
internal class HgAmendCommitService(project: Project) : AmendCommitService(project) {
  private val vcs: HgVcs get() = HgVcs.getInstance(project)!!

  override fun isAmendCommitSupported(): Boolean = vcs.version.isAmendSupported

  override fun getLastCommitMessage(root: VirtualFile): String {
    val commandExecutor = HgCommandExecutor(project)
    val args = listOf("-r", ".", "--template", "{desc}")
    val result = commandExecutor.executeInCurrentThread(root, "log", args)
    return result?.rawOutput.orEmpty()
  }
}
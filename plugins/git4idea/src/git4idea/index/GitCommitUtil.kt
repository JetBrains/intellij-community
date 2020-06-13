// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.checkin.GitCheckinEnvironment
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import java.io.File

private val LOG = Logger.getInstance("#git4idea.index.GitCommitUtil")

fun performCommit(project: Project, roots: Collection<VirtualFile>, commitMessage: String, amend: Boolean = false) {
  object : Backgroundable(project, GitBundle.message("stage.commit.process")) {
    override fun run(indicator: ProgressIndicator) {
      for (root in roots) {
        try {
          GitCheckinEnvironment.runWithMessageFile(project, root, commitMessage) { commitMessageFile: File ->
            doCommit(project, root, commitMessageFile, amend)
          }
        }
        catch (e: VcsException) {
          LOG.error("Error while committing $root", e)
        }
      }

      VcsFileUtil.markFilesDirty(project, roots)
    }
  }.queue()
}

@Throws(VcsException::class)
private fun doCommit(project: Project, root: VirtualFile, commitMessageFile: File, amend: Boolean) {
  val handler = GitLineHandler(project, root, GitCommand.COMMIT)
  handler.setStdoutSuppressed(false)
  handler.addParameters("-F")
  handler.addAbsoluteFile(commitMessageFile)
  if (amend) {
    handler.addParameters("--amend")
  }
  handler.endOptions()
  Git.getInstance().runCommand(handler).getOutputOrThrow()
}
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.progress.ProcessCanceledException
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

fun performCommit(project: Project, roots: Collection<VirtualFile>, commitMessage: String, amend: Boolean = false,
                  commitListener: CommitListener) {
  CommitTask(project, roots, commitMessage, amend, commitListener).queue()
}

class CommitTask(project: Project, private val roots: Collection<VirtualFile>,
                 private val commitMessage: String, private val amend: Boolean,
                 private val commitHandler: CommitListener) : Backgroundable(project, GitBundle.message("stage.commit.process")) {
  private val successfulRoots = mutableSetOf<VirtualFile>()
  private val failedRoots = mutableMapOf<VirtualFile, VcsException>()
  override fun run(indicator: ProgressIndicator) {
    for (root in roots) {
      try {
        GitCheckinEnvironment.runWithMessageFile(project, root, commitMessage) { commitMessageFile: File ->
          doCommit(project, root, commitMessageFile, amend)
        }
        successfulRoots.add(root)
      }
      catch (e: VcsException) {
        failedRoots[root] = e
      }
      catch (p: ProcessCanceledException) {
        throw p
      }
      catch (t: Throwable) {
        failedRoots[root] = VcsException(t)
      }
    }
    VcsFileUtil.markFilesDirty(project, roots)
  }

  override fun onFinished() {
    commitHandler.commitProcessFinished(successfulRoots, failedRoots)
  }
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

interface CommitListener {
  fun commitProcessFinished(successfulRoots: Collection<VirtualFile>, failedRoots: Map<VirtualFile, VcsException>)
}
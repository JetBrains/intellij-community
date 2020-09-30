// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin

import com.intellij.dvcs.commit.AmendCommitService
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitVersionSpecialty
import org.jetbrains.annotations.NonNls

@Service
internal class GitAmendCommitService(project: Project) : AmendCommitService(project) {
  override fun isAmendCommitSupported(): Boolean = true

  @Throws(VcsException::class)
  override fun getLastCommitMessage(root: VirtualFile): String? {
    val h = GitLineHandler(project, root, GitCommand.LOG)
    h.addParameters("--max-count=1")
    h.addParameters("--encoding=UTF-8")
    h.addParameters("--pretty=format:${getCommitMessageFormatPattern()}")
    return Git.getInstance().runCommand(h).getOutputOrThrow()
  }

  private fun getCommitMessageFormatPattern(): @NonNls String =
    if (GitVersionSpecialty.STARTED_USING_RAW_BODY_IN_FORMAT.existsIn(project)) {
      "%B"
    }
    else {
      // only message: subject + body; "%-b" means that preceding line-feeds will be deleted if the body is empty
      // %s strips newlines from subject; there is no way to work around it before 1.7.2 with %B (unless parsing some fixed format)
      "%s%n%n%-b"
    }
}
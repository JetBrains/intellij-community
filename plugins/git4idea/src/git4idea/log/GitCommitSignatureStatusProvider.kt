// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.ui.frame.VcsCommitExternalStatusPresentation
import com.intellij.vcs.log.ui.frame.VcsCommitExternalStatusProvider
import git4idea.commit.signature.GitCommitSignature
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class GitCommitSignatureStatusProvider : VcsCommitExternalStatusProvider<GitCommitSignature> {

  companion object {
    const val ID = "Git.CommitSignature"
  }

  override val id = ID

  override fun createLoader(project: Project) =
    if (SystemInfo.isWindows) NonCancellableGitCommitSignatureLoader(project)
    else SimpleGitCommitSignatureLoader(project)

  override fun getPresentation(project: Project, commit: CommitId, status: GitCommitSignature): VcsCommitExternalStatusPresentation.Signature? {
    val icon = GitCommitSignatureLogCellRenderer.getIcon(status) ?: return null
    val text = when (status) {
      is GitCommitSignature.NotVerified -> GitBundle.message("commit.signature.unverified")
      is GitCommitSignature.Verified -> GitBundle.message("commit.signature.verified")
      is GitCommitSignature.NoSignature -> ""
    }
    val tooltip = GitCommitSignatureLogCellRenderer.getToolTip(status).toString()
    return GitCommitSignatureStatusPresentation(icon, text, tooltip)
  }

  private class GitCommitSignatureStatusPresentation(override val icon: Icon,
                                                     @Nls override val shortDescriptionText: String,
                                                     @Nls override val fullDescriptionHtml: String)
    : VcsCommitExternalStatusPresentation.Signature
}
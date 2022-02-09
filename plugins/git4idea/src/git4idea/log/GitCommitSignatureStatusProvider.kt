// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.data.util.VcsCommitsDataLoader
import com.intellij.vcs.log.ui.frame.VcsCommitExternalStatusPresentation
import com.intellij.vcs.log.ui.frame.VcsCommitExternalStatusProvider
import com.intellij.vcs.log.ui.table.column.util.VcsLogExternalStatusColumnService
import git4idea.GitIcons
import git4idea.commit.signature.GitCommitSignature
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class GitCommitSignatureStatusProvider : VcsCommitExternalStatusProvider.WithColumn<GitCommitSignature>() {

  override val id = ID
  override val isColumnEnabledByDefault = false
  override val columnName = GitBundle.message("column.name.commit.signature")

  override fun createLoader(project: Project): VcsCommitsDataLoader<GitCommitSignature> {
    val loader = if (SystemInfo.isWindows) NonCancellableGitCommitSignatureLoader(project)
    else SimpleGitCommitSignatureLoader(project)
    return service<GitCommitSignatureLoaderSharedCache>().wrapWithCaching(loader)
  }

  override fun getPresentation(project: Project, status: GitCommitSignature): VcsCommitExternalStatusPresentation.Signature =
    GitCommitSignatureStatusPresentation(status)

  override fun getExternalStatusColumnService() = service<GitCommitSignatureColumnService>()

  override fun getStubStatus() = GitCommitSignature.NoSignature

  companion object {

    private const val ID = "Git.CommitSignature"

    private class GitCommitSignatureStatusPresentation(private val signature: GitCommitSignature)
      : VcsCommitExternalStatusPresentation.Signature {

      override val icon: Icon
        get() = when (signature) {
          is GitCommitSignature.Verified -> GitIcons.Verified
          is GitCommitSignature.NotVerified -> GitIcons.Signed
          GitCommitSignature.Bad -> GitIcons.Signed
          GitCommitSignature.NoSignature -> EmptyIcon.ICON_16
        }

      override val text: String
        get() = when (signature) {
          is GitCommitSignature.Verified -> GitBundle.message("commit.signature.verified")
          is GitCommitSignature.NotVerified -> GitBundle.message("commit.signature.unverified")
          GitCommitSignature.Bad -> GitBundle.message("commit.signature.bad")
          GitCommitSignature.NoSignature -> GitBundle.message("commit.signature.none")
        }

      override val description: HtmlChunk?
        get() = when (signature) {
          is GitCommitSignature.Verified -> HtmlBuilder()
            .append(GitBundle.message("commit.signature.verified")).br()
            .br()
            .append(HtmlBuilder()
                      .append(GitBundle.message("commit.signature.fingerprint")).br()
                      .append(signature.fingerprint).br()
                      .br()
                      .append(GitBundle.message("commit.signature.signed.by")).br()
                      .append(signature.user)
                      .wrapWith(HtmlChunk.span("color: ${ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())}")))
            .toFragment()
          is GitCommitSignature.NotVerified -> HtmlBuilder()
            .append(GitBundle.message("commit.signature.unverified.with.reason", getUnverifiedReason(signature.reason)))
            .toFragment()
          GitCommitSignature.Bad -> HtmlChunk.text(GitBundle.message("commit.signature.bad"))
          GitCommitSignature.NoSignature -> null
        }

      private fun getUnverifiedReason(reason: GitCommitSignature.VerificationFailureReason): @Nls String {
        return when (reason) {
          GitCommitSignature.VerificationFailureReason.UNKNOWN -> GitBundle.message("commit.signature.unverified.reason.unknown")
          GitCommitSignature.VerificationFailureReason.EXPIRED -> GitBundle.message("commit.signature.unverified.reason.expired")
          GitCommitSignature.VerificationFailureReason.EXPIRED_KEY -> GitBundle.message("commit.signature.unverified.reason.expired.key")
          GitCommitSignature.VerificationFailureReason.REVOKED_KEY -> GitBundle.message("commit.signature.unverified.reason.revoked.key")
          GitCommitSignature.VerificationFailureReason.CANNOT_VERIFY -> GitBundle.message("commit.signature.unverified.reason.cannot.verify")
        }
      }
    }
  }
}

@Service
internal class GitCommitSignatureColumnService : VcsLogExternalStatusColumnService<GitCommitSignature>() {
  override fun getDataLoader(project: Project) = GitCommitSignatureStatusProvider().createLoader(project)
}
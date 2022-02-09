// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.data.util.VcsCommitsDataLoader
import git4idea.commands.Git
import git4idea.commit.signature.GitCommitSignature
import git4idea.history.GitLogUtil
import kotlin.properties.Delegates.observable

internal abstract class GitCommitSignatureLoaderBase(private val project: Project)
  : VcsCommitsDataLoader<GitCommitSignature>, Disposable {

  private var currentIndicator by observable<ProgressIndicator?>(null) { _, old, _ ->
    old?.cancel()
  }

  final override fun loadData(commits: List<CommitId>, onChange: (Map<CommitId, GitCommitSignature>) -> Unit) {
    currentIndicator = EmptyProgressIndicator()
    val indicator = currentIndicator ?: return

    requestData(indicator, commits, onChange)
  }

  protected abstract fun requestData(indicator: ProgressIndicator,
                                     commits: List<CommitId>,
                                     onChange: (Map<CommitId, GitCommitSignature>) -> Unit)

  override fun dispose() {
    currentIndicator = null
  }

  @RequiresBackgroundThread
  @Throws(VcsException::class)
  protected fun loadCommitSignatures(
    root: VirtualFile,
    commits: Collection<Hash>
  ): Map<Hash, GitCommitSignature> {
    val h = GitLogUtil.createGitHandler(project, root)

    h.setStdoutSuppressed(true)
    h.addParameters(GitLogUtil.getNoWalkParameter(project))
    h.addParameters("--format=$COMMIT_SIGNATURES_FORMAT")
    h.addParameters(GitLogUtil.STDIN)
    h.endOptions()
    GitLogUtil.sendHashesToStdin(commits.map { it.asString() }, h)

    val output = Git.getInstance().runCommand(h).getOutputOrThrow()
    return parseSignatures(project, output.lineSequence())
  }

  private fun parseSignatures(project: Project, lines: Sequence<String>): Map<Hash, GitCommitSignature> {
    val factory = project.service<VcsLogObjectsFactory>()
    val result = mutableMapOf<Hash, GitCommitSignature>()
    val iterator = lines.iterator()

    while (iterator.hasNext()) {
      val hash = factory.createHash(iterator.next())
      val signature = createSignature(status = iterator.next(), signer = iterator.next(), fingerprint = iterator.next())

      signature?.let { result[hash] = it }
    }

    return result
  }

  /**
   * From git log man:
   *
   * "G" for a good (valid) signature,
   * "B" for a bad signature,
   * "U" for a good signature with unknown validity,
   * "X" for a good signature that has expired,
   * "Y" for a good signature made by an expired key,
   * "R" for a good signature made by a revoked key,
   * "E" if the signature cannot be checked (e.g. missing key) and
   * "N" for no signature
   */
  private fun createSignature(status: String, signer: String, fingerprint: String): GitCommitSignature? =
    when (status) {
      "G" -> GitCommitSignature.Verified(signer, fingerprint)
      "U" -> GitCommitSignature.NotVerified(GitCommitSignature.VerificationFailureReason.UNKNOWN)
      "X" -> GitCommitSignature.NotVerified(GitCommitSignature.VerificationFailureReason.EXPIRED)
      "Y" -> GitCommitSignature.NotVerified(GitCommitSignature.VerificationFailureReason.EXPIRED_KEY)
      "R" -> GitCommitSignature.NotVerified(GitCommitSignature.VerificationFailureReason.REVOKED_KEY)
      "E" -> GitCommitSignature.NotVerified(GitCommitSignature.VerificationFailureReason.CANNOT_VERIFY)
      "B" -> GitCommitSignature.Bad
      "N" -> null
      else -> null.also { LOG.error("Unknown signature status $status") }
    }

  companion object {
    private val LOG = logger<GitCommitSignatureLoaderBase>()

    private const val NEW_LINE = "%n"

    private const val HASH = "%H"
    private const val SIGNATURE_STATUS = "%G?"
    private const val SIGNER = "%GS"
    private const val FINGERPRINT = "%GF"

    private val COMMIT_SIGNATURES_FORMAT = listOf(HASH, SIGNATURE_STATUS, SIGNER, FINGERPRINT).joinToString(NEW_LINE)
  }
}
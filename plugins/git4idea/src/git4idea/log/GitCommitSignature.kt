// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runUnderIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogObjectsFactory
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.history.GitLogUtil
import git4idea.history.GitLogUtil.createGitHandler
import git4idea.history.GitLogUtil.sendHashesToStdin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed class GitCommitSignature {
  object NoSignature : GitCommitSignature()

  class Verified(val user: @NlsSafe String, val fingerprint: @NlsSafe String) : GitCommitSignature()

  object NotVerified : GitCommitSignature()
}

private const val NEW_LINE = "%n"

private const val HASH = "%H"
private const val SIGNATURE_STATUS = "%G?"
private const val SIGNER = "%GS"
private const val FINGERPRINT = "%GF"

private val COMMIT_SIGNATURES_FORMAT = listOf(HASH, SIGNATURE_STATUS, SIGNER, FINGERPRINT).joinToString(NEW_LINE)

@Throws(VcsException::class)
internal suspend fun loadCommitSignatures(
  project: Project,
  root: VirtualFile,
  commits: Collection<Hash>
): Map<Hash, GitCommitSignature> {
  val vcs = GitVcs.getInstance(project)
  val h = createGitHandler(project, root)

  h.setStdoutSuppressed(true)
  h.addParameters(GitLogUtil.getNoWalkParameter(vcs))
  h.addParameters("--format=$COMMIT_SIGNATURES_FORMAT")
  h.addParameters(GitLogUtil.STDIN)
  h.endOptions()
  sendHashesToStdin(commits.map { it.asString() }, h)

  val output = withContext(Dispatchers.IO) {
    runUnderIndicator { Git.getInstance().runCommand(h).getOutputOrThrow() }
  }
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

private fun createSignature(status: String, signer: String, fingerprint: String): GitCommitSignature? =
  when (status) {
    "B", "E" -> GitCommitSignature.NotVerified
    "G", "U", "X", "Y", "R" -> GitCommitSignature.Verified(signer, fingerprint)
    "N" -> null
    else -> null.also { LOG.error("Unknown signature status $status") }
  }

private val LOG = logger<GitCommitSignatureLoader>()

private object GitCommitSignatureLoader
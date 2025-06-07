// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.openapi.diagnostic.logger
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitUtil
import git4idea.config.GitVersion
import git4idea.config.GitVersionSpecialty
import git4idea.rebase.GitRebaseEditorHandler
import git4idea.rebase.GitRebaseOption

class GitRebaseParams internal constructor(
  private val version: GitVersion,
  branch: String?,
  newBase: String?,
  val upstream: RebaseUpstream,
  private val selectedOptions: Set<GitRebaseOption>,
  private val autoSquash: AutoSquashOption = AutoSquashOption.DEFAULT,
  val editorHandler: GitRebaseEditorHandler? = null,
) {
  companion object {
    private val LOG = logger<GitRebaseParams>()

    fun editCommits(
      version: GitVersion,
      base: String,
      editorHandler: GitRebaseEditorHandler?,
      preserveMerges: Boolean,
      autoSquash: AutoSquashOption = AutoSquashOption.DEFAULT,
    ) = editCommits(version, RebaseUpstream.Reference(base), editorHandler, preserveMerges, autoSquash)

    fun editCommits(
      version: GitVersion,
      base: RebaseUpstream,
      editorHandler: GitRebaseEditorHandler?,
      preserveMerges: Boolean,
      autoSquash: AutoSquashOption = AutoSquashOption.DEFAULT,
    ) = GitRebaseParams(version, null, null, base,
                        collectOptions(true, preserveMerges),
                        autoSquash, editorHandler)

    private fun collectOptions(interactive: Boolean, rebaseMerges: Boolean) = mutableSetOf<GitRebaseOption>().apply {
      if (interactive) {
        add(GitRebaseOption.INTERACTIVE)
      }
      if (rebaseMerges) {
        add(GitRebaseOption.REBASE_MERGES)
      }
    }
  }

  sealed interface RebaseUpstream {
    /**
     * Rebase up to the initial commit, inclusive
     */
    object Root : RebaseUpstream

    open class Reference(val ref: String) : RebaseUpstream
    class Commit(val commit: Hash) : Reference(commit.asString())

    companion object {
      fun fromRefString(ref: String): Reference {
        if (ref.isEmpty()) {
          LOG.error("Empty rebase upstream specified")
          return Reference(GitUtil.HEAD)
        }

        if (GitUtil.isHashString(ref)) {
          return Commit(HashImpl.build(ref))
        }
        return Reference(ref)
      }

      @Deprecated("Prefer using fromRefString", ReplaceWith("fromRefString(upstream)"))
      fun fromNullableString(upstream: String?): RebaseUpstream {
        if (upstream.isNullOrBlank()) return Root
        return fromRefString(upstream)
      }
    }
  }

  enum class AutoSquashOption {
    DEFAULT,
    ENABLE,
    DISABLE
  }

  val branch: String? = branch?.takeIf { it.isNotBlank() }
  val newBase: String? = newBase?.takeIf { it.isNotBlank() }

  internal constructor(
    version: GitVersion,
    branch: String?,
    newBase: String?,
    upstream: String?,
    selectedOptions: Set<GitRebaseOption>,
    autoSquash: AutoSquashOption = AutoSquashOption.DEFAULT,
    editorHandler: GitRebaseEditorHandler? = null,
  ) : this(version, branch, newBase, RebaseUpstream.fromNullableString(upstream), selectedOptions, autoSquash, editorHandler)

  constructor(version: GitVersion, upstream: String) : this(version, null, null, upstream, false, false)

  constructor(
    version: GitVersion,
    branch: String?,
    newBase: String?,
    upstream: String,
    interactive: Boolean,
    preserveMerges: Boolean,
  ) : this(version, branch, newBase, RebaseUpstream.Reference(upstream),
           collectOptions(interactive, preserveMerges), AutoSquashOption.DEFAULT)

  fun asCommandLineArguments(): List<String> = mutableListOf<String>().apply {
    selectedOptions.mapNotNull { option ->
      when (option) {
        GitRebaseOption.REBASE_MERGES -> handleRebaseMergesOption()
        GitRebaseOption.ROOT -> null // this option is converted to RebaseUpstream.Root
        else -> option.getOption(version)
      }
    }.forEach { option -> add(option) }

    when (autoSquash) {
      AutoSquashOption.DEFAULT -> {
      }
      AutoSquashOption.ENABLE -> add("--autosquash")
      AutoSquashOption.DISABLE -> add("--no-autosquash")
    }
    if (newBase != null) {
      addAll(listOf("--onto", newBase))
    }

    when (upstream) {
      RebaseUpstream.Root -> add("--root")
      is RebaseUpstream.Reference -> add(upstream.ref)
    }

    if (branch != null) {
      add(branch)
    }
  }

  fun isInteractive() = GitRebaseOption.INTERACTIVE in selectedOptions

  override fun toString(): String = asCommandLineArguments().joinToString(" ")

  private fun handleRebaseMergesOption(): String? {
    return if (!rebaseMergesAvailable() && isInteractive()) {
      LOG.error("Git rebase --preserve-merges option is incompatible with --interactive and will be omitted")
      null
    }
    else
      GitRebaseOption.REBASE_MERGES.getOption(version)
  }

  private fun rebaseMergesAvailable() = GitVersionSpecialty.REBASE_MERGES_REPLACES_PRESERVE_MERGES.existsIn(version)
}
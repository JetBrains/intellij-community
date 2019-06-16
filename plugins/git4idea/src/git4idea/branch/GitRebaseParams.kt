// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import git4idea.config.GitVersion
import git4idea.config.GitVersionSpecialty
import git4idea.rebase.GitRebaseEditorHandler

class GitRebaseParams private constructor(private val version: GitVersion,
                                          branch: String?,
                                          newBase: String?,
                                          val upstream: String,
                                          val interactive: Boolean,
                                          private val preserveMerges: Boolean,
                                          private val autoSquash: AutoSquashOption,
                                          val editorHandler: GitRebaseEditorHandler? = null) {
  companion object {
    fun editCommits(version: GitVersion,
                    base: String,
                    editorHandler: GitRebaseEditorHandler?,
                    preserveMerges: Boolean,
                    autoSquash: AutoSquashOption = AutoSquashOption.DEFAULT): GitRebaseParams =
      GitRebaseParams(version, null, null, base, true, preserveMerges, autoSquash, editorHandler)
  }

  enum class AutoSquashOption {
    DEFAULT,
    ENABLE,
    DISABLE
  }

  val branch: String? = branch?.takeIf { it.isNotBlank() }
  val newBase: String? = newBase?.takeIf { it.isNotBlank() }

  constructor(version: GitVersion, upstream: String) : this(version, null, null, upstream, false, false)

  constructor(version: GitVersion,
              branch: String?,
              newBase: String?,
              upstream: String,
              interactive: Boolean,
              preserveMerges: Boolean) : this(version, branch, newBase, upstream, interactive, preserveMerges, AutoSquashOption.DEFAULT)

  fun asCommandLineArguments(): List<String> = mutableListOf<String>().apply {
    if (interactive) {
      add("--interactive")
    }
    if (preserveMerges) {
      if (GitVersionSpecialty.REBASE_MERGES_REPLACES_PRESERVE_MERGES.existsIn(version)) {
        add("--rebase-merges")
      }
      else {
        add("--preserve-merges")
      }
    }
    when (autoSquash) {
      AutoSquashOption.DEFAULT -> {
      }
      AutoSquashOption.ENABLE -> add("--autosquash")
      AutoSquashOption.DISABLE -> add("--no-autosquash")
    }
    if (newBase != null) {
      addAll(listOf("--onto", newBase))
    }
    add(upstream)
    if (branch != null) {
      add(branch)
    }
  }

  fun isInteractive(): Boolean = interactive

  override fun toString(): String = asCommandLineArguments().joinToString(" ")
}
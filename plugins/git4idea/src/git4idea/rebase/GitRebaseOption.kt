// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import git4idea.config.GitVersion
import git4idea.config.GitVersionSpecialty.REBASE_MERGES_REPLACES_PRESERVE_MERGES
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

enum class GitRebaseOption(@NonNls private val option: String,
                           @Nls val description: String) {

  SWITCH_BRANCH("", GitBundle.message("rebase.option.switch.to.branch")),
  ONTO("--onto", GitBundle.message("rebase.option.onto")),
  REBASE_MERGES("--rebase-merges", GitBundle.message("rebase.option.rebase.merges")),
  KEEP_EMPTY("--keep-empty", GitBundle.message("rebase.option.keep.empty")),
  ROOT("--root", GitBundle.message("rebase.option.root")),
  INTERACTIVE("--interactive", GitBundle.message("rebase.option.interactive"));

  fun getOption(gitVersion: GitVersion): String {
    if (this != REBASE_MERGES) return option

    return if (REBASE_MERGES_REPLACES_PRESERVE_MERGES.existsIn(gitVersion))
      "--rebase-merges"
    else
      "--preserve-merges"
  }
}

/**
 * Set of options that are used without any additional params
 */
val REBASE_FLAGS = setOf(GitRebaseOption.INTERACTIVE,
                         GitRebaseOption.REBASE_MERGES,
                         GitRebaseOption.KEEP_EMPTY,
                         GitRebaseOption.ROOT)
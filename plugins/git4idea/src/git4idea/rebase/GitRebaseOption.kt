// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

enum class GitRebaseOption(@NonNls val option: String,
                           @Nls val description: String) {

  SWITCH_BRANCH(GitBundle.message("rebase.option.switch.to.branch"), ""),

  ONTO("--onto", GitBundle.message("rebase.option.onto")),
  INTERACTIVE("--interactive", GitBundle.message("rebase.option.interactive")),
  PRESERVE_MERGES("--preserve-merges", GitBundle.message("rebase.option.rebase.merges"));

  fun isOptionSuitable(option: GitRebaseOption): Boolean {
    return when (this) {
      SWITCH_BRANCH -> true
      ONTO -> true
      INTERACTIVE -> option != PRESERVE_MERGES
      PRESERVE_MERGES -> option != INTERACTIVE
    }
  }
}
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import git4idea.i18n.GitBundle
import org.jetbrains.annotations.NonNls

enum class RebaseOption(@NonNls val optionProvider: () -> String,
                        @NonNls val descriptionKey: String) {

  SWITCH_BRANCH({ GitBundle.message("rebase.option.switch.to.branch") }, "rebase.option.switch.to.branch.description"),

  ONTO({ "--onto" }, "rebase.option.onto"),
  INTERACTIVE({ "--interactive" }, "rebase.option.interactive"),
  PRESERVE_MERGES({ "--preserve-merges" }, "rebase.option.rebase.merges");

  val option = optionProvider()

  fun isOptionSuitable(option: RebaseOption): Boolean {
    return when (this) {
      SWITCH_BRANCH -> true
      ONTO -> true
      INTERACTIVE -> option != PRESERVE_MERGES
      PRESERVE_MERGES -> option != INTERACTIVE
    }
  }
}
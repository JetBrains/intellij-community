// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.pull

import com.intellij.openapi.util.NlsSafe
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls

enum class GitPullOption(@NlsSafe val option: String,
                         @Nls val description: String) {

  REBASE("--rebase", GitBundle.message("pull.option.rebase")),
  FF_ONLY("--ff-only", GitBundle.message("pull.option.ff.only")),
  NO_FF("--no-ff", GitBundle.message("pull.option.no.ff")),
  SQUASH("--squash", GitBundle.message("pull.option.squash.commit")),
  NO_COMMIT("--no-commit", GitBundle.message("pull.option.no.commit")),
  NO_VERIFY("--no-verify", GitBundle.message("merge.option.no.verify"));

  fun isOptionSuitable(option: GitPullOption): Boolean {
    return when (this) {
      REBASE -> option !in REBASE_INCOMPATIBLE
      FF_ONLY -> option !in FF_ONLY_INCOMPATIBLE
      NO_FF -> option !in NO_FF_INCOMPATIBLE
      SQUASH -> option !in SQUASH_COMMIT_INCOMPATIBLE
      NO_COMMIT -> option != REBASE
      else -> true
    }
  }

  companion object {
    private val REBASE_INCOMPATIBLE = listOf(FF_ONLY, NO_FF, SQUASH, NO_COMMIT)
    private val FF_ONLY_INCOMPATIBLE = listOf(NO_FF, SQUASH, REBASE)
    private val NO_FF_INCOMPATIBLE = listOf(FF_ONLY, SQUASH, REBASE)
    private val SQUASH_COMMIT_INCOMPATIBLE = listOf(NO_FF, FF_ONLY, REBASE)
  }
}
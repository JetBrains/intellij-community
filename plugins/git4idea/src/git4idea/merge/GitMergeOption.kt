// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge

import com.intellij.openapi.util.NlsSafe
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls

enum class GitMergeOption(@NlsSafe val option: String,
                          @Nls val description: String) {

  NO_FF("--no-ff", GitBundle.message("merge.option.no.ff")),
  FF_ONLY("--ff-only", GitBundle.message("merge.option.ff.only")),
  SQUASH("--squash", GitBundle.message("merge.option.squash")),
  COMMIT_MESSAGE("-m", GitBundle.message("merge.option.msg")),
  NO_COMMIT("--no-commit", GitBundle.message("merge.option.no.commit")),
  NO_VERIFY("--no-verify", GitBundle.message("merge.option.no.verify"));

  fun isOptionSuitable(option: GitMergeOption): Boolean {
    return when (this) {
      NO_FF -> option !in NO_FF_INCOMPATIBLE
      FF_ONLY -> option !in FF_ONLY_INCOMPATIBLE
      SQUASH -> option !in SQUASH_INCOMPATIBLE
      COMMIT_MESSAGE -> option !in COMMIT_MSG_INCOMPATIBLE
      NO_COMMIT -> option != COMMIT_MESSAGE
      NO_VERIFY -> true
    }
  }

  companion object {
    private val NO_FF_INCOMPATIBLE = listOf(FF_ONLY, SQUASH)
    private val FF_ONLY_INCOMPATIBLE = listOf(NO_FF, SQUASH, COMMIT_MESSAGE)
    private val SQUASH_INCOMPATIBLE = listOf(NO_FF, FF_ONLY, COMMIT_MESSAGE)
    private val COMMIT_MSG_INCOMPATIBLE = listOf(FF_ONLY, SQUASH, NO_COMMIT)
  }
}
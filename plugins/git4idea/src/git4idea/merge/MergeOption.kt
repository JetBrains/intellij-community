// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge

import org.jetbrains.annotations.NonNls

enum class MergeOption(@NonNls val option: String,
                       @NonNls val descriptionKey: String) {

  NO_FF("--no-ff", "merge.option.no.ff"),
  FF_ONLY("--ff-only", "merge.option.ff.only"),
  SQUASH("--squash", "merge.option.squash"),
  COMMIT_MESSAGE("-m", "merge.option.msg"),
  NO_COMMIT("--no-commit", "merge.option.no.commit"),
  NO_VERIFY("--no-verify", "merge.option.no.verify");

  fun isOptionSuitable(option: MergeOption): Boolean {
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
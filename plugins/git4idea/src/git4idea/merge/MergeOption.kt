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
      NO_FF -> option != SQUASH && option != FF_ONLY
      SQUASH -> option == SQUASH || option == NO_COMMIT
      COMMIT_MESSAGE -> option != SQUASH && option != NO_COMMIT
      NO_COMMIT -> option != COMMIT_MESSAGE
      NO_VERIFY -> true
      FF_ONLY -> option != NO_FF
    }
  }
}
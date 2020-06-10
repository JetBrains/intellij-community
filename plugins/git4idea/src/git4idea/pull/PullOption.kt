// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.pull

import org.jetbrains.annotations.NonNls

enum class PullOption(@NonNls val option: String,
                      @NonNls val descriptionKey: String) {

  REBASE("--rebase", "pull.option.rebase"),
  FF_ONLY("--ff-only", "pull.option.ff.only"),
  NO_FF("--no-ff", "pull.option.no.ff"),
  SQUASH_COMMIT("--squash", "pull.option.squash.commit"),
  NO_COMMIT("--no-commit", "pull.option.no.commit"),
  NO_VERIFY("--no-verify", "merge.option.no.verify");

  fun isOptionSuitable(option: PullOption): Boolean {
    if (this != REBASE && option == REBASE) return false

    return when (this) {
      REBASE -> option == REBASE
      FF_ONLY -> option != NO_FF
      NO_FF -> option != FF_ONLY
      SQUASH_COMMIT -> option == SQUASH_COMMIT || option == NO_COMMIT
      else -> true
    }
  }
}
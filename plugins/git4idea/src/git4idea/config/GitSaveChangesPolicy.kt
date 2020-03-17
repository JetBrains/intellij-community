// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

/**
 * The way the local changes are saved before update
 */
enum class GitSaveChangesPolicy(val storageName: String,
                                val verb: String,
                                val verbInPast: String,
                                val oppositeVerb: String) {

  STASH("stash", "stash", "stashed", "unstash"),
  SHELVE("shelf", "shelve", "shelved", "unshelve");

}
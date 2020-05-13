// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

/**
 * The way the local changes are saved before update
 */
enum class GitSaveChangesPolicy {

  STASH {
    override fun selectBundleMessage(stashMessage: String, shelfMessage: String) = stashMessage
  },
  SHELVE {
    override fun selectBundleMessage(stashMessage: String, shelfMessage: String) = shelfMessage
  };

  abstract fun selectBundleMessage(stashMessage: String, shelfMessage: String): String
}
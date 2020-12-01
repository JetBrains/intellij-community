// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.search

import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls

// higher weight -> higher position
enum class GitSearchEverywhereItemType(val weight: Int) {

  COMMIT_BY_HASH(0 - 10) {
    override val displayName: String
      get() = GitBundle.message("search.everywhere.items.commit.by.hash")
  },
  LOCAL_BRANCH(0 - 20) {
    override val displayName: String
      get() = GitBundle.message("search.everywhere.items.local.branch")
  },
  REMOTE_BRANCH(0 - 30) {
    override val displayName: String
      get() = GitBundle.message("search.everywhere.items.remote.branch")
  },
  TAG(0 - 40) {
    override val displayName: String
      get() = GitBundle.message("search.everywhere.items.tag")
  },
  COMMIT_BY_MESSAGE(0 - 50) {
    override val displayName: String
      get() = GitBundle.message("search.everywhere.items.commit.by.message")
  };

  @get:Nls
  abstract val displayName: String
}

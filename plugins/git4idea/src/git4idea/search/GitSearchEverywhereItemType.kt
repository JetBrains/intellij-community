// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.search

// higher weight -> higher position
enum class GitSearchEverywhereItemType(val weight: Int) {
  COMMIT_BY_HASH(0 - 10),
  LOCAL_BRANCH(0 - 20),
  REMOTE_BRANCH(0 - 30),
  TAG(0 - 40),
  COMMIT_BY_MESSAGE(0 - 50)
}

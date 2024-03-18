/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log

import com.intellij.openapi.vfs.VirtualFile

/**
 * Allows to retrieve information about commits in the log, such as commit indexes and commit details.
 *
 * @see VcsLogCommitDataCache
 * @see com.intellij.vcs.log.data.VcsLogStorage
 */
interface VcsLogDataProvider {

  /**
   * Cache for retrieving [VcsFullCommitDetails] by commit index.
   */
  val fullCommitDetailsCache: VcsLogCommitDataCache<VcsFullCommitDetails>

  /**
   * Cache for retrieving [VcsCommitMetadata] by commit index.
   */
  val commitMetadataCache: VcsLogCommitDataCache<VcsCommitMetadata>

  /**
   * Returns a commit in a form of [CommitId] for a specified index or null if this index does not correspond to any commit.
   *
   * @param commitIndex index of a commit
   * @return commit identified by this index or null
   */
  fun getCommitId(commitIndex: Int): CommitId?

  /**
   * Returns a unique integer identifier for a commit with specified hash and root.
   *
   * @param hash commit hash
   * @param root root of the repository for the commit
   * @return commit index
   */
  fun getCommitIndex(hash: Hash, root: VirtualFile): Int
}
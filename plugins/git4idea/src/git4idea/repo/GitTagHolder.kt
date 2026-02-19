// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitTag
import org.jetbrains.annotations.ApiStatus

/**
 * @deprecated Use [GitRepositoryTagsHolder] instead
 * @see [GitRepository.getTagsHolder]
 */
@Deprecated("Use GitRepositoryTagsHolder instead")
@ApiStatus.ScheduledForRemoval
class GitTagHolder(private val repository: GitRepository) {

  fun getTag(hash: String): GitTag? {
    val state = repository.tagsHolder.state.value
    return state.commitHashesToTags[HashImpl.build(hash)]?.firstOrNull()
  }

  fun getTags(): Map<GitTag, Hash> {
    val state = repository.tagsHolder.state.value
    return state.tagsToCommitHashes
  }
}

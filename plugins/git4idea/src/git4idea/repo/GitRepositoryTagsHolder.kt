// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.util.messages.Topic
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitTag
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.NonExtendable
interface GitRepositoryTagsHolder {
  /**
   * Contains the current state of repository tags.
   */
  val state: StateFlow<GitRepositoryTagsState>

  fun reload()

  companion object {
    val TAGS_UPDATED: Topic<GitTagsHolderListener> = Topic.create("Git Tags updated", GitTagsHolderListener::class.java)
  }
}

fun interface GitTagsHolderListener : EventListener {
  fun tagsUpdated(repository: GitRepository)
}

fun GitRepositoryTagsHolder.getTagsForCommit(hash: String): List<GitTag> =
  state.value.commitHashesToTags[HashImpl.build(hash)].orEmpty()

val GitRepositoryTagsHolder.tags: Set<GitTag> get() = state.value.tagsToCommitHashes.keys

@ApiStatus.NonExtendable
sealed interface GitRepositoryTagsState {
  /**
   * Map from Git tags to their corresponding commit hash
   */
  val tagsToCommitHashes: Map<GitTag, Hash>

  /**
   * Map from commit hashes to their corresponding Git tags (multiple tags can point to the same commit)
   */
  val commitHashesToTags: Map<Hash, List<GitTag>>

  /**
   * Initial state of [GitRepositoryTagsHolder.state] before the tags have been loaded
   */
  data object NotLoaded : GitRepositoryTagsState {
    override val tagsToCommitHashes: Map<GitTag, Hash> = emptyMap()
    override val commitHashesToTags: Map<Hash, List<GitTag>> = emptyMap()
  }
}

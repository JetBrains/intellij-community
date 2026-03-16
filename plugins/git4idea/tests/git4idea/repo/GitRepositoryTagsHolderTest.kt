// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitTag
import git4idea.test.GitSingleRepoTest
import git4idea.test.last

class GitRepositoryTagsHolderTest : GitSingleRepoTest() {

  fun `test lightweight tag points to commit`() {
    val commitHash = last()
    val tag = GitTag("lightweight-tag")
    git("tag ${tag.name}")

    val tagsHolder = GitRepositoryTagsHolderImpl(repo)
    val state = tagsHolder.loadTagsFromGit()

    val hash = HashImpl.build(commitHash)
    val expectedState = GitRepositoryTagsHolderImpl.LoadedState(
      tagsToCommitHashes = mapOf(tag to hash),
      commitHashesToTags = mapOf(hash to listOf(tag))
    )

    assertEquals(expectedState, state)
  }

  fun `test annotated tag points to commit not tag object`() {
    val commitHash = last()
    val tag = GitTag("annotated-tag")
    git("tag -a ${tag.name} -m 'Annotated tag message'")

    val tagsHolder = GitRepositoryTagsHolderImpl(repo)
    val state = tagsHolder.loadTagsFromGit()

    val hash = HashImpl.build(commitHash)
    val expectedState = GitRepositoryTagsHolderImpl.LoadedState(
      tagsToCommitHashes = mapOf(tag to hash),
      commitHashesToTags = mapOf(hash to listOf(tag))
    )

    assertEquals(expectedState, state)
  }

  fun `test multiple tags on same commit`() {
    val commitHash = last()
    val tag1 = GitTag("tag1")
    val tag2 = GitTag("tag2")
    git("tag ${tag1.name}")
    git("tag -a ${tag2.name} -m 'Annotated tag'")

    val tagsHolder = GitRepositoryTagsHolderImpl(repo)
    val state = tagsHolder.loadTagsFromGit()

    val hash = HashImpl.build(commitHash)

    assertEquals(mapOf(tag1 to hash, tag2 to hash), state.tagsToCommitHashes)
    assertEquals(setOf(tag1, tag2), state.commitHashesToTags[hash]?.toSet())
  }

  fun `test empty repository returns empty loaded state`() {
    val tagsHolder = GitRepositoryTagsHolderImpl(repo)
    val state = tagsHolder.loadTagsFromGit()

    val expectedState = GitRepositoryTagsHolderImpl.LoadedState(
      tagsToCommitHashes = emptyMap(),
      commitHashesToTags = emptyMap()
    )

    assertEquals(expectedState, state)
  }
}

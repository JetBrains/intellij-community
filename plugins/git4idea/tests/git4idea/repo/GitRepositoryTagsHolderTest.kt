// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitTag
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.last
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitRepositoryTagsHolderTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test lightweight tag points to commit`(): Unit = with(context) {
    val commitHash = last()
    val tag = GitTag("lightweight-tag")
    git("tag ${tag.name}")

    val state = GitRepositoryTagsHolderImpl(repo).loadTagsFromGit()

    val hash = HashImpl.build(commitHash)
    val expectedState = GitRepositoryTagsHolderImpl.LoadedState(
      tagsToCommitHashes = mapOf(tag to hash),
      commitHashesToTags = mapOf(hash to listOf(tag))
    )

    assertThat(state).isEqualTo(expectedState)
  }

  @Test
  fun `test annotated tag points to commit not tag object`(): Unit = with(context) {
    val commitHash = last()
    val tag = GitTag("annotated-tag")
    git("tag -a ${tag.name} -m 'Annotated tag message'")

    val state = GitRepositoryTagsHolderImpl(repo).loadTagsFromGit()

    val hash = HashImpl.build(commitHash)
    val expectedState = GitRepositoryTagsHolderImpl.LoadedState(
      tagsToCommitHashes = mapOf(tag to hash),
      commitHashesToTags = mapOf(hash to listOf(tag))
    )

    assertThat(state).isEqualTo(expectedState)
  }

  @Test
  fun `test multiple tags on same commit`(): Unit = with(context) {
    val commitHash = last()
    val tag1 = GitTag("tag1")
    val tag2 = GitTag("tag2")
    git("tag ${tag1.name}")
    git("tag -a ${tag2.name} -m 'Annotated tag'")

    val state = GitRepositoryTagsHolderImpl(repo).loadTagsFromGit()

    val hash = HashImpl.build(commitHash)

    assertThat(state.tagsToCommitHashes).isEqualTo(mapOf(tag1 to hash, tag2 to hash))
    assertThat(state.commitHashesToTags[hash]).containsExactlyInAnyOrder(tag1, tag2)
  }

  @Test
  fun `test empty repository returns empty loaded state`(): Unit = with(context) {
    val state = GitRepositoryTagsHolderImpl(repo).loadTagsFromGit()

    val expectedState = GitRepositoryTagsHolderImpl.LoadedState(
      tagsToCommitHashes = emptyMap(),
      commitHashesToTags = emptyMap()
    )

    assertThat(state).isEqualTo(expectedState)
  }
}

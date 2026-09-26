// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.RefGroup
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.impl.SingletonRefGroup
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitRefGroupsTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test single tracked branch`(): Unit = with(context) {
    check(given("HEAD", "master", "origin/master"),
          listOf("HEAD"),
          "Local" to listOf("master"),
          "origin/..." to listOf("origin/master"))
  }

  @Test
  fun `test single local branch`(): Unit = with(context) {
    check(given("HEAD", "master"),
          listOf("HEAD"),
          "Local" to listOf("master"))
  }

  @Test
  fun `test local tracked and remote branch`(): Unit = with(context) {
    check(given("HEAD", "master", "origin/master", "origin/remote_branch", "local_branch"),
          listOf("HEAD"),
          "Local" to listOf("master", "local_branch"),
          "origin/..." to listOf("origin/master", "origin/remote_branch"))
  }

  private fun GitSingleRepoContext.check(
    actual: Collection<VcsRef>,
    expectedSingleGroups: List<String>,
    vararg expectedOtherGroups: Pair<String, List<String>>,
  ) {
    val actualGroups = GitRefManager(project, repositoryManager).groupForBranchFilter(actual)

    val singleGroups = actualGroups.filterIsInstance<SingletonRefGroup>()
    assertThat(singleGroups.map { it.name }).isEqualTo(expectedSingleGroups)

    val otherGroups = actualGroups.filter { it !is SingletonRefGroup }
    assertThat(otherGroups.map { group: RefGroup -> group.name to group.refs.map { it.name } })
      .isEqualTo(expectedOtherGroups.toList())
  }
}

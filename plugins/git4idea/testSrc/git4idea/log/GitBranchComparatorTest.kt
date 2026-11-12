// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.VcsRef
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitBranchComparatorTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test tracked remote branch is more important than its local branch`(): Unit = with(context) {
    check("origin/139", given("origin/139", "139"))
  }

  @Test
  fun `test tracked remote branch is more important than any local branch`(): Unit = with(context) {
    check("origin/139", given("feature", "origin/139", "139"))
  }

  @Test
  fun `test untracked remote branch is more important than any local branch`(): Unit = with(context) {
    check("origin/139", given("feature", "origin/139"))
  }

  @Test
  fun `test master is more important than other local branches`(): Unit = with(context) {
    check("master", given("feature", "master"))
  }

  @Test
  fun `test origin master is more important than other remote branches`(): Unit = with(context) {
    check("origin/master", given("origin/139", "master", "139", "origin/master", "origin/135"))
  }

  @Test
  fun `test local branch is more important than tag`(): Unit = with(context) {
    check("feature", given("refs/tags/v1", "feature", "refs/tags/v2"))
  }

  @Test
  fun `test two local non-tracking branches are compared lexicographically`(): Unit = with(context) {
    check("feature", given("zoo", "feature"))
  }

  @Test
  fun `test tracked local branch is not special`(): Unit = with(context) {
    val refs = given("zoo", "origin/zoo", "feature").filter { it.name != "origin/zoo" }
    assertThat(getTheMostPowerfulRef(refs).name).isEqualTo("feature")
  }

  @Test
  fun `test remote branches are compared lexicographically`(): Unit = with(context) {
    check("origin/feature", given("origin/feature", "origin/zoo", "zoo"))
  }

  @Test
  fun `test local branch is more important than HEAD`(): Unit = with(context) {
    check("feature", given("feature", "HEAD"))
  }

  @Test
  fun `test tag is more important than HEAD`(): Unit = with(context) {
    check("refs/tags/v1", given("refs/tags/v1", "HEAD"))
  }

  private fun GitSingleRepoContext.check(expectedBest: String, givenBranches: Collection<VcsRef>) {
    assertThat(getTheMostPowerfulRef(givenBranches)).isEqualTo(expect(expectedBest)[0])
  }

  private fun GitSingleRepoContext.getTheMostPowerfulRef(givenBranches: Collection<VcsRef>): VcsRef {
    val comparator = GitRefManager(project, repositoryManager).branchLayoutComparator
    return givenBranches.sortedWith(comparator)[0]
  }
}

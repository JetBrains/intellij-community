// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.VcsRef
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitLabelComparatorTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test empty`(): Unit = with(context) {
    check(emptyList(), emptyList())
  }

  @Test
  fun `test single`(): Unit = with(context) {
    check(given("HEAD"),
          expect("HEAD"))
  }

  @Test
  fun `test HEAD is at left from branch`(): Unit = with(context) {
    check(given("master", "HEAD"),
          expect("HEAD", "master"))
  }

  @Test
  fun `test local branches are compared as strings`(): Unit = with(context) {
    check(given("release", "feature"),
          expect("feature", "release"))
  }

  @Test
  fun `test tag is to the right of remote branch`(): Unit = with(context) {
    check(given("refs/tags/v1", "origin/master"),
          expect("origin/master", "refs/tags/v1"))
  }

  @Test
  fun `test master is to the left of other local branches`(): Unit = with(context) {
    check(given("feature", "master"),
          expect("master", "feature"))
  }

  @Test
  fun `test origin master is to the left of other remote branches`(): Unit = with(context) {
    check(given("origin/master", "origin/aaa"),
          expect("origin/master", "origin/aaa"))
  }

  @Test
  fun `test remote tracking branch is not special`(): Unit = with(context) {
    check(given("feature", "origin/aaa", "origin/feature"),
          expect("feature", "origin/aaa", "origin/feature"))
  }

  @Test
  fun `test complex 1`(): Unit = with(context) {
    check(given("refs/tags/v1", "feature", "HEAD", "master"),
          expect("HEAD", "master", "feature", "refs/tags/v1"))
  }

  @Test
  fun `test complex 2`(): Unit = with(context) {
    check(given("origin/master", "origin/great_feature", "refs/tags/v1", "release", "HEAD", "master"),
          expect("HEAD", "master", "origin/master", "release", "origin/great_feature", "refs/tags/v1"))
  }

  // may happen e.g. in multi-repo case
  @Test
  fun `test two masters`(): Unit = with(context) {
    check(given("master", "master"),
          expect("master", "master"))
  }

  @Test
  fun `test current branch first`(): Unit = with(context) {
    git("checkout -b zzz")
    check(given("xxx", "zzz", "yyy"),
          expect("zzz", "xxx", "yyy"))
  }

  private fun GitSingleRepoContext.check(unsorted: Collection<VcsRef>, expected: List<VcsRef>) {
    // for the sake of simplicity we check only names of references
    assertThat(sort(unsorted).map { it.name })
      .describedAs("Sorted refs don't match")
      .isEqualTo(expected.map { it.name })
  }

  private fun GitSingleRepoContext.sort(refs: Collection<VcsRef>): List<VcsRef> =
    refs.sortedWith(GitRefManager(project, repositoryManager).labelsOrderComparator)
}

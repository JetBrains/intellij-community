// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import git4idea.config.GitPushTargetHistoryEntry
import git4idea.test.cd
import git4idea.test.git
import git4idea.test.makeCommit
import git4idea.test.makePushSpec
import git4idea.test.runUnderProgress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Collections.singletonMap

@TestApplication
internal class GitRecentPushTargetsTest {

  private val fixture = gitPushSingleRepoFixture()
  private val context: GitPushSingleRepoContext get() = fixture.get()

  @Test
  fun `push to a custom target branch is recorded`(): Unit = with(context) {
    makeCommit("file.txt")
    push("master", "origin/feature")

    val entries = recentTargets("master")
    assertThat(entries).hasSize(1)
    assertThat(entries[0].repositoryRootPath).isEqualTo(repository.root.path)
    assertThat(entries[0].sourceBranch).isEqualTo("master")
    assertThat(entries[0].targetRemote).isEqualTo("origin")
    assertThat(entries[0].targetBranch).isEqualTo("feature")
  }

  @Test
  fun `push to the default target is not recorded`(): Unit = with(context) {
    makeCommit("file.txt")
    push("master", "origin/master")

    assertThat(recentTargets("master")).isEmpty()
  }

  @Test
  fun `push of a new branch under its own name is not recorded`(): Unit = with(context) {
    cd(repository)
    git("checkout -b feature")
    push("feature", "origin/feature")

    assertThat(recentTargets("feature")).isEmpty()
  }

  @Test
  fun `rejected push is not recorded`(): Unit = with(context) {
    cd(broRepo)
    makeCommit("bro.txt")
    git("push origin master:feature")

    cd(repository)
    val result = push("master", "origin/feature")

    assertThat(result.results[repository]!!.type).isEqualTo(GitPushRepoResult.Type.REJECTED_NO_FF)
    assertThat(recentTargets("master")).isEmpty()
  }

  @Test
  fun `most recently used target is first`(): Unit = with(context) {
    makeCommit("file.txt")
    push("master", "origin/review-a")
    push("master", "origin/review-b")
    assertThat(recentTargets("master").map { it.targetBranch }).containsExactly("review-b", "review-a")

    // The repeated push is up to date and must move the target to the front.
    push("master", "origin/review-a")
    assertThat(recentTargets("master").map { it.targetBranch }).containsExactly("review-a", "review-b")
  }

  @Test
  fun `history keeps at most 10 targets in total`(): Unit = with(context) {
    val root = repository.root.path
    for (i in 1..11) {
      settings.addRecentPushTarget(root, "master", "origin", "review-$i")
    }

    val names = settings.getRecentPushTargets(root, "master").map { it.targetBranch }
    assertThat(names).hasSize(10)
    assertThat(names.first()).isEqualTo("review-11")
    assertThat(names).doesNotContain("review-1")

    // The cap is global: a target of another branch evicts the oldest entry.
    settings.addRecentPushTarget(root, "feature", "origin", "review-f")
    assertThat(settings.getRecentPushTargets(root, "feature")).hasSize(1)
    val remaining = settings.getRecentPushTargets(root, "master").map { it.targetBranch }
    assertThat(remaining).hasSize(9)
    assertThat(remaining).doesNotContain("review-2")
  }

  @Test
  fun `targets are keyed by repository and source branch`(): Unit = with(context) {
    val root = repository.root.path
    settings.addRecentPushTarget(root, "master", "origin", "review-a")
    settings.addRecentPushTarget(root, "feature", "origin", "review-b")
    settings.addRecentPushTarget("/other/root", "master", "origin", "review-c")

    assertThat(settings.getRecentPushTargets(root, "master").map { it.targetBranch }).containsExactly("review-a")
    assertThat(settings.getRecentPushTargets(root, "feature").map { it.targetBranch }).containsExactly("review-b")
  }

  private fun GitPushSingleRepoContext.recentTargets(sourceBranch: String): List<GitPushTargetHistoryEntry> {
    return settings.getRecentPushTargets(repository.root.path, sourceBranch)
  }

  private fun GitPushSingleRepoContext.push(from: String, to: String): GitPushResult {
    updateRepositories()
    refresh()
    updateChangeListManager()

    val spec = makePushSpec(repository, from, to)
    return runUnderProgress {
      GitPushOperation(project, pushSupport, singletonMap(repository, spec), null, false, false).execute()
    }
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitCommitShaWithPatches
import git4idea.changes.filePath
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.repo.GitRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.findViewedStateSha
import org.junit.jupiter.api.Test

private const val MERGE_BASE_SHA = "merge-base"
private const val COMMIT_A = "commit-a"
private const val COMMIT_B = "commit-b"
private const val FILE_X = "x.txt"
private const val FILE_Y = "y.txt"

/**
 * Commit A changes the file X. The later commit B changes only the file Y.
 */
@TestApplication
class GitLabMergeRequestViewedStateTest {
  private val root = mockk<VirtualFile> {
    every { path } returns "/repo"
    every { url } returns "file:///repo"
  }
  private val repository = mockk<GitRepository> {
    every { root } returns this@GitLabMergeRequestViewedStateTest.root
  }
  private val mergeRequest = mockk<GitLabMergeRequest> {
    every { serverPath } returns GitLabServerPath("https://gitlab.com")
    every { projectId } returns "42"
    every { iid } returns "5"
    every { gitRemote } returns GitRemoteUrlCoordinates("https://gitlab.com/owner/repo.git", mockk(), repository)
  }
  private val parsedChanges = GitBranchComparisonResult.create(
    mockk(), root, MERGE_BASE_SHA, MERGE_BASE_SHA,
    listOf(
      GitCommitShaWithPatches(COMMIT_A, listOf(MERGE_BASE_SHA), listOf(patch(FILE_X))),
      GitCommitShaWithPatches(COMMIT_B, listOf(COMMIT_A), listOf(patch(FILE_Y))),
    ),
    listOf(patch(FILE_X), patch(FILE_Y))
  )
  private val viewedState = GitLabPersistentMergeRequestChangesViewedState()

  @Test
  fun `a cumulative change uses the last commit that changed the file instead of the head`() {
    val changeX = cumulativeChange(FILE_X)

    assertThat(parsedChanges.findViewedStateSha(repository, changeX)).isEqualTo(COMMIT_A)
  }

  @Test
  fun `a cumulative change of a file in the head commit uses the head`() {
    val changeY = cumulativeChange(FILE_Y)

    assertThat(parsedChanges.findViewedStateSha(repository, changeY)).isEqualTo(COMMIT_B)
  }

  @Test
  fun `a change of a single commit has no viewed state sha`() {
    val commitChangeY = parsedChanges.changesByCommits.getValue(COMMIT_B).single()

    assertThat(parsedChanges.findViewedStateSha(repository, commitChangeY)).isNull()
  }

  @Test
  fun `a viewed file stores the last commit that changed it`() {
    val changeX = cumulativeChange(FILE_X)

    viewedState.markViewed(mergeRequest, parsedChanges, listOf(changeX), viewed = true)

    assertThat(viewedState.state.states.single().viewedFiles).isEqualTo(mapOf(FILE_X to COMMIT_A))
    assertThat(viewedState.isViewed(mergeRequest, parsedChanges, changeX)).isTrue()
    assertThat(viewedState.isViewed(mergeRequest, parsedChanges, cumulativeChange(FILE_Y))).isFalse()
  }

  @Test
  fun `marking a file as not viewed removes its viewed state`() {
    val changeX = cumulativeChange(FILE_X)
    viewedState.markViewed(mergeRequest, parsedChanges, listOf(changeX), viewed = true)

    viewedState.markViewed(mergeRequest, parsedChanges, listOf(changeX), viewed = false)

    assertThat(viewedState.isViewed(mergeRequest, parsedChanges, changeX)).isFalse()
  }

  @Test
  fun `a change of a single commit does not change the viewed state`() {
    viewedState.markViewed(mergeRequest, parsedChanges, listOf(cumulativeChange(FILE_X)), viewed = true)

    viewedState.markViewed(mergeRequest, parsedChanges, listOf(commitChange(COMMIT_A)), viewed = false)
    viewedState.markViewed(mergeRequest, parsedChanges, listOf(commitChange(COMMIT_B)), viewed = true)

    assertThat(viewedState.state.states.single().viewedFiles).isEqualTo(mapOf(FILE_X to COMMIT_A))
    assertThat(viewedState.isViewed(mergeRequest, parsedChanges, commitChange(COMMIT_A))).isFalse()
  }

  private fun cumulativeChange(path: String): RefComparisonChange =
    parsedChanges.changes.single { parsedChanges.patchesByChange.getValue(it).patch.filePath == path }

  private fun commitChange(sha: String): RefComparisonChange =
    parsedChanges.changesByCommits.getValue(sha).single()
}

private fun patch(path: String): TextFilePatch {
  val text = buildString {
    append("diff --git a/").append(path).append(" b/").append(path).append("\n")
    append("--- a/").append(path).append("\n")
    append("+++ b/").append(path).append("\n")
    append("@@ -1,1 +1,1 @@\n")
    append("-old\n")
    append("+new\n")
  }
  return PatchReader(text).readTextPatches().single()
}

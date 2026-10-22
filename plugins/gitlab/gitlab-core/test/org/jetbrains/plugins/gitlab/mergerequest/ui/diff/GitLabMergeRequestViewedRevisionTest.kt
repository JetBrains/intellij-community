// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.diff

import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitCommitShaWithPatches
import git4idea.changes.GitTextFilePatchWithHistory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabPersistentMergeRequestChangesViewedState
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.charset.StandardCharsets

internal class GitLabMergeRequestViewedRevisionTest {
  @Test
  fun `opening an older changed file preserves its manually viewed revision`() = runTest {
    val saved = mutableMapOf("/repo/x.txt" to "commit-a")
    val vm = createViewModel(backgroundScope, saved)

    vm.markViewed()
    vm.markViewed()

    assertEquals(mapOf("/repo/x.txt" to "commit-a"), saved)
  }

  @Test
  fun `automatically marks an unviewed file with its last change revision`() = runTest {
    val saved = mutableMapOf<String, String>()

    createViewModel(backgroundScope, saved).markViewed()

    assertEquals(mapOf("/repo/x.txt" to "commit-a"), saved)
  }

  @Test
  fun `a later change to the file records the newer revision`() = runTest {
    val saved = mutableMapOf("/repo/x.txt" to "commit-a")

    createViewModel(backgroundScope, saved, lastCommitPath = "x.txt").markViewed()

    assertEquals(mapOf("/repo/x.txt" to "commit-b"), saved)
  }

  @Test
  fun `a single commit diff does not mark the cumulative file viewed`() = runTest {
    val saved = mutableMapOf<String, String>()

    createViewModel(backgroundScope, saved, cumulative = false).markViewed()

    assertTrue(saved.isEmpty())
  }

  @Test
  fun `missing file history does not overwrite an existing viewed revision`() = runTest {
    val saved = mutableMapOf("/repo/x.txt" to "commit-a")

    createViewModel(backgroundScope, saved, firstCommitPath = "other.txt").markViewed()

    assertEquals(mapOf("/repo/x.txt" to "commit-a"), saved)
  }

  private fun createViewModel(
    cs: CoroutineScope,
    saved: MutableMap<String, String>,
    cumulative: Boolean = true,
    firstCommitPath: String = "x.txt",
    lastCommitPath: String = "y.txt",
  ): GitLabMergeRequestDiffReviewViewModelImpl {
    val viewedState = mockk<GitLabPersistentMergeRequestChangesViewedState>()
    every { viewedState.markViewed(any(), any(), any(), any(), any(), true) } answers {
      arg<Iterable<Pair<FilePath, String>>>(4).forEach { (path, sha) -> saved[path.path] = sha }
    }
    val project = mockk<Project>()
    every { project.getService(GitLabPersistentMergeRequestChangesViewedState::class.java) } returns viewedState
    val mergeRequest = mockk<GitLabMergeRequest>(relaxed = true)
    every { mergeRequest.gitRemote.repository.root.path } returns "/repo"
    every { mergeRequest.gitRemote.repository.root.url } returns "file:///repo"
    every { mergeRequest.details.value.diffRefs?.headSha } returns "commit-b"
    every { mergeRequest.discussions } returns emptyFlow()
    every { mergeRequest.draftNotes } returns emptyFlow()
    val path = mockk<FilePath>()
    every { path.path } returns "/repo/x.txt"
    every { path.ioFile } returns File("/repo/x.txt")
    val change = RefComparisonChange(mockk(), path, mockk(), path)
    val parsedChanges = mockk<GitBranchComparisonResult>()
    every { parsedChanges.commits } returns listOf(
      commit("commit-a", firstCommitPath),
      commit("commit-b", lastCommitPath),
    )
    val diffVm = mockk<GitLabMergeRequestDiffViewModel>()
    every { diffVm.discussions } returns MutableStateFlow(ComputedResult.loading())
    every { diffVm.draftDiscussions } returns MutableStateFlow(ComputedResult.loading())
    every { diffVm.newDiscussions } returns MutableStateFlow(emptyList())
    val discussions = mockk<GitLabMergeRequestDiscussionsViewModels>()
    every { discussions.newDiscussions } returns MutableStateFlow(emptyList())
    return GitLabMergeRequestDiffReviewViewModelImpl(
      project, cs, mergeRequest, GitTextFilePatchWithHistory(TextFilePatch(StandardCharsets.UTF_8), cumulative, mockk()),
      change, parsedChanges, diffVm, discussions, MutableStateFlow(DiscussionsViewOption.ALL), mockk(), mockk(),
    )
  }

  private fun commit(sha: String, path: String): GitCommitShaWithPatches =
    GitCommitShaWithPatches(sha, emptyList(), listOf(TextFilePatch(StandardCharsets.UTF_8).apply {
      beforeName = path
      afterName = path
    }))
}

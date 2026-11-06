// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase.createChildData
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import git4idea.GitRevisionNumber
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitCommitShaWithPatches
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt(writeIntent = true)
class GitLabChangesContainerTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private lateinit var mainDir: VirtualFile
  private lateinit var testDir: VirtualFile

  @BeforeEach
  fun setUp() {
    val module = projectModel.createModule()
    val moduleDir = projectModel.baseProjectDir.newVirtualDirectory("module")
    mainDir = projectModel.baseProjectDir.newVirtualDirectory("module/src/main/kotlin")
    testDir = projectModel.baseProjectDir.newVirtualDirectory("module/src/test/kotlin")
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addSourceRoot(module, mainDir)
    PsiTestUtil.addSourceRoot(module, testDir, true)
  }

  @Test
  fun `hiding test sources drops a test file`() {
    val source = modified(createChildData(mainDir, "Log.kt"))
    val test = modified(createChildData(testDir, "LogTest.kt"))

    assertThat(create(listOf(source, test), hideTestSources = true).summaryChanges).containsExactly(source)
  }

  @Test
  fun `hiding test sources judges a deleted file by its parent directory`() {
    val deletedSource = deleted("${mainDir.path}/gone/Gone.kt")
    val deletedTest = deleted("${testDir.path}/gone/GoneTest.kt")

    assertThat(create(listOf(deletedSource, deletedTest), hideTestSources = true).summaryChanges).containsExactly(deletedSource)
  }

  @Test
  fun `test sources are counted for the merge request and for each commit, hidden or shown`() {
    val source = modified(createChildData(mainDir, "Log.kt"))
    val test = modified(createChildData(testDir, "LogTest.kt"))
    val changesByCommits = mapOf("b" to listOf(source, test), "c" to listOf(source))

    for (hideTestSources in listOf(true, false)) {
      val container = create(listOf(source, test), hideTestSources, changesByCommits)

      assertThat(container.testSourcesOf(null)).isEqualTo(GitLabTestSources(1, hideTestSources))
      assertThat(container.testSourcesOf("b")).isEqualTo(GitLabTestSources(1, hideTestSources))
      assertThat(container.testSourcesOf("c")).isEqualTo(GitLabTestSources(0, hideTestSources))
      assertThat(container.testSourcesOf("unknown")).isEqualTo(GitLabTestSources(0, hideTestSources))
    }
  }

  @Test
  fun `the nearest shown change follows a hidden selected change, else precedes it`() {
    val first = deleted("${mainDir.path}/A.kt")
    val hidden = deleted("${testDir.path}/ATest.kt")
    val last = deleted("${mainDir.path}/B.kt")
    val selection = ChangesSelection.Precise(listOf(first, hidden, last), hidden)

    assertThat(selection.findNearestChangeIn(listOf(first, hidden, last))).isEqualTo(hidden)
    assertThat(selection.findNearestChangeIn(listOf(first, last))).isEqualTo(last)
    assertThat(selection.findNearestChangeIn(listOf(first))).isEqualTo(first)
    assertThat(selection.findNearestChangeIn(emptyList())).isNull()
  }

  @Test
  fun `hiding test sources filters the changes of each commit and keeps the comparison result complete`() {
    val source = modified(createChildData(mainDir, "Log.kt"))
    val test = modified(createChildData(testDir, "LogTest.kt"))

    val container = create(listOf(source, test), hideTestSources = true)

    assertThat(container.changesByCommits["b"]).containsExactly(source)
    assertThat(container.changes.changes).containsExactly(source, test)
  }

  @Test
  fun `showing test sources keeps every change`() {
    val source = modified(createChildData(mainDir, "Log.kt"))
    val test = modified(createChildData(testDir, "LogTest.kt"))

    assertThat(create(listOf(source, test), hideTestSources = false).summaryChanges).containsExactly(source, test)
  }

  private fun modified(file: VirtualFile): RefComparisonChange {
    val path = LocalFilePath(file.path, false)
    return RefComparisonChange(GitRevisionNumber("a"), path, GitRevisionNumber("b"), path)
  }

  private fun deleted(path: String): RefComparisonChange =
    RefComparisonChange(GitRevisionNumber("a"), LocalFilePath(path, false), GitRevisionNumber("b"), null)

  private fun create(
    changes: List<RefComparisonChange>,
    hideTestSources: Boolean,
    changesByCommits: Map<String, List<RefComparisonChange>> = mapOf("b" to changes),
  ): GitLabChangesContainer =
    runBlocking(Dispatchers.Default) {
      GitLabChangesContainer.create(projectModel.project, ComparisonResult(changes, changesByCommits), hideTestSources)
    }

  private class ComparisonResult(
    override val changes: List<RefComparisonChange>,
    override val changesByCommits: Map<String, List<RefComparisonChange>>,
  ) : GitBranchComparisonResult {
    override val baseSha: String = "base"
    override val mergeBaseSha: String = "base"
    override val headSha: String = "b"
    override val commits: List<GitCommitShaWithPatches> = emptyList()
    override val patchesByChange: Map<RefComparisonChange, GitTextFilePatchWithHistory> = emptyMap()
  }
}

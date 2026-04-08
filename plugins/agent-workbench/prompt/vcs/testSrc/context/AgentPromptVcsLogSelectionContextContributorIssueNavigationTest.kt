// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.context

import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.agent.workbench.prompt.core.array
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vcs.IssueNavigationConfiguration
import com.intellij.openapi.vcs.IssueNavigationLink
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.data.LoadingDetails
import com.intellij.vcs.log.impl.VcsCommitMetadataImpl
import com.intellij.vcs.log.util.VcsUserUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.function.Consumer

@TestApplication
class AgentPromptVcsLogSelectionContextContributorIssueNavigationTest {
  private val contributor = AgentPromptVcsLogSelectionContextContributor()

  @Test
  fun resolvesIssueUrlsFromCachedMetadata() {
    withIssueNavigationLinks(IssueNavigationLink("[A-Z]+-\\d+", "https://tracker.example.test/issue/$0")) {
      val hash = "%040x".format(1)
      val root = LightVirtualFile("root-a")
      val selection = TestVcsLogCommitSelection(
        selectedCommits = listOf(commit(hash, root)),
        cachedMetadata = listOf(commitMetadata(hash, root)),
      )
      val dataContext = SimpleDataContext.builder()
        .add(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION, selection)
        .build()

      val result = contributor.collect(invocationData(dataContext))

      val entries = result.single().payload.objOrNull()?.array("entries")!!.map { value -> value.objOrNull()!! }
      val issueUrls = entries.single().array("issueUrls")!!.map { value -> (value as AgentPromptPayloadValue.Str).value }
      assertEquals(listOf("https://tracker.example.test/issue/TEST-101"), issueUrls)
    }
  }

  @Test
  fun ignoresLoadingMetadataWhenResolvingIssueUrls() {
    withIssueNavigationLinks(IssueNavigationLink("[A-Z]+-\\d+", "https://tracker.example.test/issue/$0")) {
      val hash = "%040x".format(1)
      val root = LightVirtualFile("root-a")
      val selection = TestVcsLogCommitSelection(
        selectedCommits = listOf(commit(hash, root)),
        cachedMetadata = listOf(loadingCommitMetadata(hash, root)),
      )
      val dataContext = SimpleDataContext.builder()
        .add(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION, selection)
        .build()

      val result = contributor.collect(invocationData(dataContext))

      val entries = result.single().payload.objOrNull()?.array("entries")!!.map { value -> value.objOrNull()!! }
      assertNull(entries.single().array("issueUrls"))
    }
  }

  private fun withIssueNavigationLinks(vararg links: IssueNavigationLink, action: () -> Unit) {
    val project = ProjectManager.getInstance().defaultProject
    val configuration = IssueNavigationConfiguration.getInstance(project)
    val previousLinks = configuration.links.toList()
    configuration.setLinks(links.toList())
    try {
      action()
    }
    finally {
      configuration.setLinks(previousLinks)
    }
  }

  private fun invocationData(dataContext: DataContext?): AgentPromptInvocationData {
    val project = ProjectManager.getInstance().defaultProject
    val attributes = if (dataContext == null) {
      emptyMap()
    }
    else {
      mapOf(AGENT_PROMPT_VCS_INVOCATION_DATA_CONTEXT_KEY to dataContext)
    }
    return AgentPromptInvocationData(
      project = project,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent",
      actionPlace = "Vcs.Log.Ui",
      invokedAtMs = 0L,
      attributes = attributes,
    )
  }

  private fun commit(hash: String, root: LightVirtualFile): CommitId {
    return CommitId(testHash(hash), root)
  }

  private fun commitMetadata(hash: String, root: LightVirtualFile): VcsCommitMetadata {
    return TestCommitMetadata(testHash(hash), root)
  }

  private fun loadingCommitMetadata(hash: String, root: LightVirtualFile): VcsCommitMetadata {
    return TestLoadingCommitMetadata(testHash(hash), root)
  }

  private fun testHash(value: String): Hash {
    return object : Hash {
      override fun asString(): String = value

      override fun toShortString(): String = value.take(7)
    }
  }

  private open class TestCommitMetadata(hash: Hash, root: LightVirtualFile) :
    VcsCommitMetadataImpl(hash, emptyList(), 0L, root, TEST_SUBJECT, TEST_USER, TEST_SUBJECT, TEST_USER, 0L)

  private class TestLoadingCommitMetadata(hash: Hash, root: LightVirtualFile) :
    TestCommitMetadata(hash, root), LoadingDetails

  private class TestVcsLogCommitSelection(
    selectedCommits: List<CommitId>,
    override val cachedMetadata: List<VcsCommitMetadata> = emptyList(),
    override val cachedFullDetails: List<VcsFullCommitDetails> = emptyList(),
  ) : VcsLogCommitSelection {
    override val rows: IntArray = IntArray(selectedCommits.size) { index -> index }
    override val ids: List<VcsLogCommitStorageIndex> = rows.toList()
    override val commits: List<CommitId> = selectedCommits

    override fun requestFullDetails(consumer: Consumer<in List<VcsFullCommitDetails>>) {
      consumer.accept(emptyList())
    }
  }

  private companion object {
    const val TEST_SUBJECT = "Fix TEST-101 regression"
    val TEST_USER = VcsUserUtil.createUser("Test User", "test@example.com")
  }
}

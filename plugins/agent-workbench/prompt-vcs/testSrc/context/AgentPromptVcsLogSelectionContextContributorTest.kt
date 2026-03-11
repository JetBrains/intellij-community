// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.context

import com.intellij.agent.workbench.prompt.vcs.AgentPromptVcsBundle
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.array
import com.intellij.agent.workbench.sessions.core.prompt.number
import com.intellij.agent.workbench.sessions.core.prompt.objOrNull
import com.intellij.agent.workbench.sessions.core.prompt.string
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.TextRevisionNumber
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsLogDataKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.function.Consumer

@TestApplication
class AgentPromptVcsLogSelectionContextContributorTest {
  private val contributor = AgentPromptVcsLogSelectionContextContributor()

  @Test
  fun returnsEmptyWhenInvocationHasNoDataContext() {
    val result = contributor.collect(invocationData(dataContext = null))

    assertThat(result).isEmpty()
  }

  @Test
  fun returnsEmptyWhenInvocationDoesNotContainVcsSelection() {
    val dataContext = SimpleDataContext.builder().build()

    val result = contributor.collect(invocationData(dataContext = dataContext))

    assertThat(result).isEmpty()
  }

  @Test
  fun collectsSelectedCommitsAsHashOnlyContext() {
    val firstHash = "1111111111111111111111111111111111111111"
    val secondHash = "2222222222222222222222222222222222222222"
    val selection = TestVcsLogCommitSelection(
      selectedCommits = listOf(
        commit(hash = firstHash, rootName = "root-a"),
        commit(hash = secondHash, rootName = "root-b"),
      )
    )
    val dataContext = SimpleDataContext.builder()
      .add(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION, selection)
      .build()

    val result = contributor.collect(invocationData(dataContext = dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    val payload = item.payload.objOrNull()!!
    val entries = payload.array("entries")!!.map { value -> value.objOrNull()!! }

    assertThat(item.rendererId).isEqualTo(AgentPromptContextRendererIds.VCS_COMMITS)
    assertThat(item.title).isEqualTo(AgentPromptVcsBundle.message("context.vcs.title"))
    assertThat(item.itemId).isEqualTo("vcsLog.commits")
    assertThat(item.parentItemId).isNull()
    assertThat(item.source).isEqualTo("vcsLog")
    assertThat(item.body.lineSequence().toList()).containsExactly(firstHash, secondHash)
    assertThat(payload.number("selectedCount")).isEqualTo("2")
    assertThat(payload.number("includedCount")).isEqualTo("2")
    assertThat(entries.map { entry -> entry.string("hash") }).containsExactly(firstHash, secondHash)
    assertThat(entries.map { entry -> entry.string("rootPath") }).allMatch { rootPath -> !rootPath.isNullOrBlank() }
    assertThat(item.truncation.reason).isEqualTo(AgentPromptContextTruncationReason.NONE)
  }

  @Test
  fun truncatesSelectionToConfiguredLimit() {
    val selection = TestVcsLogCommitSelection(
      selectedCommits = (1..24).map { index ->
        commit(
          hash = "%040x".format(index),
          rootName = "root-$index",
        )
      }
    )
    val dataContext = SimpleDataContext.builder()
      .add(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION, selection)
      .build()

    val result = contributor.collect(invocationData(dataContext = dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    val payload = item.payload.objOrNull()!!

    assertThat(item.body.lineSequence().toList()).hasSize(20)
    assertThat(payload.number("selectedCount")).isEqualTo("24")
    assertThat(payload.number("includedCount")).isEqualTo("20")
    assertThat(item.truncation.reason).isEqualTo(AgentPromptContextTruncationReason.SOURCE_LIMIT)
  }

  @Test
  fun fallsBackToRevisionNumbersWhenLogCommitSelectionIsMissing() {
    val dataContext = SimpleDataContext.builder()
      .add(
        VcsDataKeys.VCS_REVISION_NUMBERS,
        arrayOf(
          TextRevisionNumber("abc123"),
          TextRevisionNumber("  "),
          TextRevisionNumber("abc123"),
          TextRevisionNumber("def456"),
        )
      )
      .build()

    val result = contributor.collect(invocationData(dataContext = dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    val payload = item.payload.objOrNull()!!
    val entries = payload.array("entries")!!.map { value -> value.objOrNull()!! }

    assertThat(item.body.lineSequence().toList()).containsExactly("abc123", "def456")
    assertThat(payload.number("selectedCount")).isEqualTo("2")
    assertThat(payload.number("includedCount")).isEqualTo("2")
    assertThat(entries.map { entry -> entry.string("hash") }).containsExactly("abc123", "def456")
    assertThat(entries.map { entry -> entry.string("rootPath") }).allMatch { rootPath -> rootPath == null }
  }

  @Test
  fun fallsBackToSingleRevisionNumberWhenArrayAndLogSelectionAreMissing() {
    val dataContext = SimpleDataContext.builder()
      .add(VcsDataKeys.VCS_REVISION_NUMBER, TextRevisionNumber("single987"))
      .build()

    val result = contributor.collect(invocationData(dataContext = dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    val payload = item.payload.objOrNull()!!
    val entries = payload.array("entries")!!.map { value -> value.objOrNull()!! }

    assertThat(item.body).isEqualTo("single987")
    assertThat(payload.number("selectedCount")).isEqualTo("1")
    assertThat(payload.number("includedCount")).isEqualTo("1")
    assertThat(entries.single().string("hash")).isEqualTo("single987")
    assertThat(entries.single().string("rootPath")).isNull()
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

  private fun commit(hash: String, rootName: String): CommitId {
    return CommitId(testHash(hash), LightVirtualFile(rootName))
  }

  private fun testHash(value: String): Hash {
    return object : Hash {
      override fun asString(): String = value

      override fun toShortString(): String = value.take(7)
    }
  }

  private class TestVcsLogCommitSelection(
    selectedCommits: List<CommitId>,
  ) : VcsLogCommitSelection {
    override val rows: IntArray = IntArray(selectedCommits.size) { index -> index }
    override val ids: List<VcsLogCommitStorageIndex> = rows.toList()
    override val commits: List<CommitId> = selectedCommits
    override val cachedMetadata: List<VcsCommitMetadata> = emptyList()
    override val cachedFullDetails: List<VcsFullCommitDetails> = emptyList()

    override fun requestFullDetails(consumer: Consumer<in List<VcsFullCommitDetails>>) {
      consumer.accept(emptyList())
    }
  }
}


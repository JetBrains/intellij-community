// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextPickerRequest
import com.intellij.agent.workbench.prompt.core.array
import com.intellij.agent.workbench.prompt.core.number
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.vcs.AgentPromptVcsBundle
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import javax.swing.JPanel

@TestApplication
class AgentPromptVcsCommitManualContextSourceTest {
  @Test
  fun resolveEligibleRootPathsPrefersRootsContainingWorkingProjectPath() {
    val eligibleRoots = resolveEligibleRootPaths(
      rootPaths = listOf("/repo", "/other"),
      workingProjectPath = "/repo/project-a",
    )

    assertThat(eligibleRoots).containsExactly("/repo")
  }

  @Test
  fun resolveEligibleRootPathsFallsBackToAllRootsWhenNoRootsMatch() {
    val eligibleRoots = resolveEligibleRootPaths(
      rootPaths = listOf("/repo", "/other"),
      workingProjectPath = "/unknown/project",
    )

    assertThat(eligibleRoots).containsExactlyInAnyOrder("/repo", "/other")
  }

  @Test
  fun buildManualVcsContextItemCapsSelectionAndPreservesPayloadShape() {
    val selection = (1..22).map { index ->
      commitEntry(hash = "hash-%02d".format(index), rootPath = "/repo")
    }

    val item = buildManualVcsContextItem(selection)

    assertThat(item.title).isEqualTo(AgentPromptVcsBundle.message("context.vcs.manual.title"))
    assertThat(item.itemId).isEqualTo("manual.vcs.commits")
    assertThat(item.source).isEqualTo("manualVcs")
    assertThat(item.truncation.reason).isEqualTo(AgentPromptContextTruncationReason.SOURCE_LIMIT)
    assertThat(item.body.lineSequence().toList()).hasSize(20)
    assertThat(item.payload.objOrNull()?.array("entries")).hasSize(20)
    assertThat(item.payload.objOrNull()?.number("selectedCount")).isEqualTo("22")
    assertThat(item.payload.objOrNull()?.number("includedCount")).isEqualTo("20")
  }

  @Test
  fun extractCurrentHashesReadsExistingPayloadEntries() {
    val item = buildManualVcsContextItem(
      listOf(
        commitEntry(hash = "abc12345", rootPath = "/repo"),
        commitEntry(hash = "def67890", rootPath = "/repo"),
      )
    )

    assertThat(extractCurrentHashes(item)).containsExactly("abc12345", "def67890")
  }

  @Test
  fun buildManualVcsContextItemIncludesIssueUrlsInPayload() {
    val item = buildManualVcsContextItem(
      listOf(
        commitEntry(
          hash = "abc12345",
          rootPath = "/repo",
          issueUrls = listOf("https://youtrack.jetbrains.com/issue/IJPL-123456"),
        )
      )
    )

    val entry = item.payload.objOrNull()?.array("entries")?.single()?.objOrNull()
    val issueUrls = entry?.array("issueUrls")
      ?.mapNotNull { value -> (value as? AgentPromptPayloadValue.Str)?.value }

    assertThat(issueUrls).containsExactly("https://youtrack.jetbrains.com/issue/IJPL-123456")
  }

  @Test
  fun normalizeManualVcsSelectionDeduplicatesAndTrimsHashes() {
    val normalized = normalizeManualVcsSelection(
      listOf(
        commitEntry(hash = "  abc12345  ", rootPath = "/repo"),
        commitEntry(hash = "abc12345", rootPath = "/other"),
        commitEntry(hash = "", rootPath = "/repo"),
        commitEntry(hash = "def67890", rootPath = "/repo"),
      )
    )

    assertThat(normalized.map { it.hash }).containsExactly("abc12345", "def67890")
    assertThat(normalized.first().rootPath).isEqualTo("/repo")
  }

  @Test
  fun showPickerChecksVcsAvailabilityAgainstSourceProject() {
    val hostProject = projectProxy(name = "Agent Dedicated Frame", basePath = "/dedicated")
    val sourceProject = projectProxy(name = "Source Project", basePath = "/repo")
    var queriedProject: Project? = null
    var errorMessage: String? = null
    val source = AgentPromptVcsCommitManualContextSource(
      projectLogAvailability = { project ->
        queriedProject = project
        false
      },
    )

    source.showPicker(
      AgentPromptManualContextPickerRequest(
        hostProject = hostProject,
        sourceProject = sourceProject,
        invocationData = invocationData(hostProject),
        workingProjectPath = "/repo",
        currentItems = emptyList(),
        anchorComponent = JPanel(),
        onSelected = { error("Selection callback is not expected") },
        onError = { message -> errorMessage = message },
      )
    )

    assertThat(queriedProject).isSameAs(sourceProject)
    assertThat(errorMessage).isEqualTo(AgentPromptVcsBundle.message("manual.context.vcs.error.unavailable"))
  }

  private fun commitEntry(hash: String, rootPath: String?, issueUrls: List<String> = emptyList()): CommitPickerEntry {
    return CommitPickerEntry(
      commitIndex = 1,
      hash = hash,
      subject = "Fix issue",
      rootPath = rootPath,
      rootName = rootPath?.substringAfterLast('/'),
      issueUrls = issueUrls,
    )
  }

  private fun invocationData(project: Project): AgentPromptInvocationData {
    return AgentPromptInvocationData(
      project = project,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent with Context",
      actionPlace = "test",
      invokedAtMs = 0,
    )
  }

  private fun projectProxy(name: String, basePath: String?): Project {
    val handler = InvocationHandler { proxy, method, args ->
      when (method.name) {
        "getName" -> name
        "getBasePath" -> basePath
        "isOpen" -> true
        "isDisposed" -> false
        "toString" -> "MockProject($name)"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> null
      }
    }
    return Proxy.newProxyInstance(
      ProjectManager::class.java.classLoader,
      arrayOf(Project::class.java),
      handler,
    ) as Project
  }
}

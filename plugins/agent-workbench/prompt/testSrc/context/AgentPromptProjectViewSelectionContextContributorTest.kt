// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.number
import com.intellij.agent.workbench.sessions.core.prompt.objOrNull
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class AgentPromptProjectViewSelectionContextContributorTest {
  private val contributor = AgentPromptProjectViewSelectionContextContributor()

  @Test
  fun aggregatesAndTruncatesSelectionToConfiguredLimit() {
    val selected = createPhysicalSelection((1..7).map { index -> "File$index.kt" to "fun f$index() = $index" })
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, selected.toTypedArray())
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    val payload = item.payload.objOrNull()!!
    assertThat(item.rendererId).isEqualTo(AgentPromptContextRendererIds.PATHS)
    assertThat(item.source).isEqualTo("projectView")
    assertThat(payload.number("selectedCount")).isEqualTo("7")
    assertThat(payload.number("includedCount")).isEqualTo("5")
    assertThat(item.truncation.reason).isEqualTo(AgentPromptContextTruncationReason.SOURCE_LIMIT)
    assertThat(item.body.lineSequence().toList()).hasSize(5)
    assertThat(item.body.lineSequence().all { line -> line.startsWith("file: ") }).isTrue()
  }

  @Test
  fun usesSingleVirtualFileSelectionWhenArrayIsMissing() {
    val selected = createPhysicalFile("README.md", "# readme")
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.VIRTUAL_FILE, selected)
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    val payload = item.payload.objOrNull()!!
    assertThat(item.rendererId).isEqualTo(AgentPromptContextRendererIds.PATHS)
    assertThat(payload.number("selectedCount")).isEqualTo("1")
    assertThat(payload.number("includedCount")).isEqualTo("1")
    assertThat(item.truncation.reason).isEqualTo(AgentPromptContextTruncationReason.NONE)
    assertThat(payload.number("fileCount")).isEqualTo("1")
    assertThat(payload.number("directoryCount")).isEqualTo("0")
    assertThat(item.body).contains("file: ")
  }

  @Test
  fun skipsNonPhysicalVirtualFiles() {
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.VIRTUAL_FILE, LightVirtualFile("Scratch.kts", "println(1)"))
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).isEmpty()
  }

  @Test
  fun skipsDotPathEntries() {
    val dotPathFile = object : LightVirtualFile("empty") {
      override fun getPath(): String = "."
    }
    val selected = createPhysicalFile("real.txt", "ok")
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(dotPathFile, selected))
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    val payload = item.payload.objOrNull()!!
    assertThat(payload.number("selectedCount")).isEqualTo("1")
    assertThat(payload.number("includedCount")).isEqualTo("1")
    assertThat(item.body.lineSequence().all { line -> line.contains("real.txt") }).isTrue()
  }

  private fun invocationData(dataContext: com.intellij.openapi.actionSystem.DataContext): AgentPromptInvocationData {
    val project = ProjectManager.getInstance().defaultProject
    return AgentPromptInvocationData(
      project = project,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent",
      actionPlace = "ProjectViewPopup",
      invokedAtMs = 0L,
      attributes = mapOf(
        AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY to dataContext,
      ),
    )
  }

  private fun createPhysicalSelection(entries: List<Pair<String, String>>): List<VirtualFile> {
    val root = Files.createTempDirectory("aw-project-selection")
    return entries.map { (name, content) ->
      createPhysicalFile(root, name, content)
    }
  }

  private fun createPhysicalFile(name: String, content: String): VirtualFile {
    val root = Files.createTempDirectory("aw-project-selection-single")
    return createPhysicalFile(root, name, content)
  }

  private fun createPhysicalFile(root: Path, name: String, content: String): VirtualFile {
    val nioPath = root.resolve(name)
    Files.writeString(nioPath, content)
    return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(nioPath))
  }
}

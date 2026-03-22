// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.number
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JTree

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
    assertThat(item.itemId).isEqualTo("projectView.selection")
    assertThat(item.parentItemId).isNull()
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
    assertThat(item.itemId).isEqualTo("projectView.selection")
    assertThat(item.parentItemId).isNull()
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

  @Test
  fun skipsWhenContextIsNonProjectViewTree() {
    val selected = createPhysicalSelection(listOf("File1.kt" to "fun f1() = 1"))
    val tree = JTree()
    val toolWindow = createToolWindow("Commit")
    val dataContext = testDataContext(
      CommonDataKeys.VIRTUAL_FILE_ARRAY to selected.toTypedArray(),
      PlatformCoreDataKeys.CONTEXT_COMPONENT to tree,
      PlatformDataKeys.TOOL_WINDOW to toolWindow,
    )

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).isEmpty()
  }

  @Test
  fun skipsWhenContextIsTreeWithoutToolWindow() {
    val selected = createPhysicalSelection(listOf("File1.kt" to "fun f1() = 1"))
    val tree = JTree()
    val dataContext = testDataContext(
      CommonDataKeys.VIRTUAL_FILE_ARRAY to selected.toTypedArray(),
      PlatformCoreDataKeys.CONTEXT_COMPONENT to tree,
    )

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).isEmpty()
  }

  @Test
  fun returnsPathsWhenContextIsProjectViewTree() {
    val selected = createPhysicalSelection(listOf("File1.kt" to "fun f1() = 1"))
    val tree = JTree()
    val toolWindow = createToolWindow(ToolWindowId.PROJECT_VIEW)
    val dataContext = testDataContext(
      CommonDataKeys.VIRTUAL_FILE_ARRAY to selected.toTypedArray(),
      PlatformCoreDataKeys.CONTEXT_COMPONENT to tree,
      PlatformDataKeys.TOOL_WINDOW to toolWindow,
    )

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    assertThat(result.single().rendererId).isEqualTo(AgentPromptContextRendererIds.PATHS)
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

  private fun createToolWindow(id: String): ToolWindow {
    return Proxy.newProxyInstance(
      ToolWindow::class.java.classLoader,
      arrayOf(ToolWindow::class.java),
    ) { _, method, _ -> if (method.name == "getId") id else null } as ToolWindow
  }

  private fun testDataContext(vararg entries: Pair<DataKey<*>, Any>): DataContext {
    val map = entries.associate { (key, value) -> key.name to value }
    return DataContext { dataId ->
      @Suppress("UNCHECKED_CAST")
      map[dataId]
    }
  }
}

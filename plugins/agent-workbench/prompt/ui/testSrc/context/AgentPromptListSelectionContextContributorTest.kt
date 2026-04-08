// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.number
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.ColoredListCellRenderer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.BorderLayout
import java.lang.reflect.Proxy
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

@TestApplication
class AgentPromptListSelectionContextContributorTest {
  private val contributor = AgentPromptListSelectionContextContributor()

  @Test
  fun extractsSelectedListEntryFromRendererInsideParentPanel() {
    val list = JList(arrayOf(
      TestListEntry("repo-main", "main", "~/workspace/repo-main"),
      TestListEntry("feature-worktree", "feature-branch", "~/workspace/feature-worktree"),
    )).apply {
      cellRenderer = object : ColoredListCellRenderer<TestListEntry>() {
        override fun customizeCellRenderer(
          list: JList<out TestListEntry?>,
          value: TestListEntry?,
          index: Int,
          selected: Boolean,
          hasFocus: Boolean,
        ) {
          if (value == null) return
          append(value.name)
          append(value.branch)
          append(value.path)
        }
      }
      selectedIndex = 1
    }
    val panel = JPanel(BorderLayout()).apply {
      add(list, BorderLayout.CENTER)
    }
    val dataContext = testDataContext(
      PlatformCoreDataKeys.CONTEXT_COMPONENT to panel,
      PlatformDataKeys.TOOL_WINDOW to createVersionControlToolWindow(),
    )

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    assertThat(item.rendererId).isEqualTo(AgentPromptContextRendererIds.SNIPPET)
    assertThat(item.itemId).isEqualTo("list.selection")
    assertThat(item.source).isEqualTo("list")
    assertThat(item.title).contains("Version Control")
    assertThat(item.body).contains("Selected:")
    assertThat(item.body).contains("feature-worktree")
    assertThat(item.body).contains("feature-branch")
    assertThat(item.body).contains("~/workspace/feature-worktree")
    val payload = item.payload.objOrNull()!!
    assertThat(payload.number("selectedCount")).isEqualTo("1")
    assertThat(payload.number("includedCount")).isEqualTo("1")
    assertThat(item.truncation.reason).isEqualTo(AgentPromptContextTruncationReason.NONE)
  }

  @Test
  fun extractsSelectedTableRowsAsPipeSeparatedText() {
    val table = JTable(DefaultTableModel(arrayOf(arrayOf("feature", "~/idea/feature")), arrayOf("Branch", "Path"))).apply {
      setRowSelectionInterval(0, 0)
    }
    val dataContext = testDataContext(
      PlatformCoreDataKeys.CONTEXT_COMPONENT to table,
    )

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    assertThat(item.body).contains("feature")
    assertThat(item.body).contains("~/idea/feature")
    assertThat(item.body).contains("feature | ~/idea/feature")
  }

  @Test
  fun fallsBackToSelectedItemsWhenNoListComponentIsAvailable() {
    val dataContext = testDataContext(
      PlatformCoreDataKeys.CONTEXT_COMPONENT to JPanel(),
      PlatformCoreDataKeys.SELECTED_ITEMS to arrayOf("alpha", "beta"),
    )

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    assertThat(item.title).isEqualTo("Selection")
    assertThat(item.body).contains("- alpha")
    assertThat(item.body).contains("- beta")
    val payload = item.payload.objOrNull()!!
    assertThat(payload.number("selectedCount")).isEqualTo("2")
    assertThat(payload.number("includedCount")).isEqualTo("2")
  }

  private fun invocationData(dataContext: DataContext): AgentPromptInvocationData {
    val project = ProjectManager.getInstance().defaultProject
    return AgentPromptInvocationData(
      project = project,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent",
      actionPlace = "MainMenu",
      invokedAtMs = 0L,
      attributes = mapOf(
        AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY to dataContext,
      ),
    )
  }

  private fun createVersionControlToolWindow(): ToolWindow {
    return Proxy.newProxyInstance(
      ToolWindow::class.java.classLoader,
      arrayOf(ToolWindow::class.java),
    ) { _, method, _ -> if (method.name == "getId") "Version Control" else null } as ToolWindow
  }

  private fun testDataContext(vararg entries: Pair<DataKey<*>, Any>): DataContext {
    val map = entries.associate { (key, value) -> key.name to value }
    return DataContext { dataId ->
      @Suppress("UNCHECKED_CAST")
      map[dataId]
    }
  }

  private data class TestListEntry(
    val name: String,
    val branch: String,
    val path: String,
  )
}

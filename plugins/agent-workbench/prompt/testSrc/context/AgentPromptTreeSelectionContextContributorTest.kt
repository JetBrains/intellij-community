// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.number
import com.intellij.agent.workbench.sessions.core.prompt.objOrNull
import com.intellij.agent.workbench.sessions.core.prompt.string
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

@TestApplication
class AgentPromptTreeSelectionContextContributorTest {
  private val contributor = AgentPromptTreeSelectionContextContributor()

  @Test
  fun returnsEmptyWhenContextComponentIsNotJTree() {
    val dataContext = testDataContext(
      PlatformCoreDataKeys.CONTEXT_COMPONENT to javax.swing.JPanel(),
    )

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).isEmpty()
  }

  @Test
  fun returnsEmptyWhenTreeHasNoSelection() {
    val tree = createTree("Root", listOf("Child1", "Child2"))
    tree.clearSelection()
    val dataContext = testDataContext(
      PlatformCoreDataKeys.CONTEXT_COMPONENT to tree,
    )

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).isEmpty()
  }

  @Test
  fun extractsSingleSelectedNodeText() {
    val tree = createTree("Root", listOf("MyItem"))
    selectNodes(tree, listOf("MyItem"))
    val dataContext = testDataContext(
      PlatformCoreDataKeys.CONTEXT_COMPONENT to tree,
    )

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    assertThat(item.rendererId).isEqualTo(AgentPromptContextRendererIds.SNIPPET)
    assertThat(item.itemId).isEqualTo("tree.selection")
    assertThat(item.source).isEqualTo("tree")
    assertThat(item.body).contains("MyItem")
    assertThat(item.body).contains("Tree: Tree")
    val payload = item.payload.objOrNull()!!
    assertThat(payload.number("selectedCount")).isEqualTo("1")
    assertThat(payload.number("includedCount")).isEqualTo("1")
    assertThat(item.truncation.reason).isEqualTo(AgentPromptContextTruncationReason.NONE)
  }

  @Test
  fun handlesMultiSelectionWithDeduplication() {
    val tree = createTree("Root", listOf("Alpha", "Beta", "Alpha", "Gamma"))
    selectNodes(tree, listOf("Alpha", "Beta", "Alpha", "Gamma"))
    val dataContext = testDataContext(
      PlatformCoreDataKeys.CONTEXT_COMPONENT to tree,
    )

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    val payload = item.payload.objOrNull()!!
    // "Alpha" appears twice in tree but deduplication keeps unique values
    assertThat(payload.number("selectedCount")).isEqualTo("3")
    assertThat(payload.number("includedCount")).isEqualTo("3")
    assertThat(item.body).contains("- Alpha")
    assertThat(item.body).contains("- Beta")
    assertThat(item.body).contains("- Gamma")
  }

  @Test
  fun truncatesToMaxNodes() {
    val children = (1..15).map { "Node$it" }
    val tree = createTree("Root", children)
    selectNodes(tree, children)
    val dataContext = testDataContext(
      PlatformCoreDataKeys.CONTEXT_COMPONENT to tree,
    )

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    val payload = item.payload.objOrNull()!!
    assertThat(payload.number("selectedCount")).isEqualTo("15")
    assertThat(payload.number("includedCount")).isEqualTo("10")
    assertThat(item.truncation.reason).isEqualTo(AgentPromptContextTruncationReason.SOURCE_LIMIT)
  }

  @Test
  fun filtersOutBlankToStringResults() {
    val root = DefaultMutableTreeNode("Root")
    root.add(DefaultMutableTreeNode("Valid"))
    root.add(DefaultMutableTreeNode(""))
    root.add(DefaultMutableTreeNode("   "))
    val tree = JTree(DefaultTreeModel(root))
    selectNodes(tree, listOf("Valid", "", "   "))
    val dataContext = testDataContext(
      PlatformCoreDataKeys.CONTEXT_COMPONENT to tree,
    )

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    val payload = item.payload.objOrNull()!!
    assertThat(payload.number("selectedCount")).isEqualTo("1")
    assertThat(item.body).contains("- Valid")
    assertThat(item.body).doesNotContain("- \n")
  }

  @Test
  fun returnsEmptyWhenDataContextIsMissing() {
    val result = contributor.collect(invocationDataWithoutContext())

    assertThat(result).isEmpty()
  }

  @Test
  fun usesToolWindowIdAsTreeKind() {
    val tree = createTree("Root", listOf("Item"))
    selectNodes(tree, listOf("Item"))
    val toolWindow = java.lang.reflect.Proxy.newProxyInstance(
      ToolWindow::class.java.classLoader,
      arrayOf(ToolWindow::class.java),
    ) { _, method, _ -> if (method.name == "getId") "Version Control" else null } as ToolWindow
    val dataContext = testDataContext(
      PlatformCoreDataKeys.CONTEXT_COMPONENT to tree,
      PlatformDataKeys.TOOL_WINDOW to toolWindow,
    )

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    assertThat(item.title).contains("Version Control")
    assertThat(item.body).contains("Tree: Version Control")
    val payload = item.payload.objOrNull()!!
    assertThat(payload.string("treeKind")).isEqualTo("Version Control")
  }

  @Test
  fun fallsToToolWindowThenComponentNameThenDefault() {
    val tree = createTree("Root", listOf("Item"))
    selectNodes(tree, listOf("Item"))
    tree.accessibleContext.accessibleName = null
    val dataContext = testDataContext(
      PlatformCoreDataKeys.CONTEXT_COMPONENT to tree,
    )

    // Falls back to component name when no accessible name and no tool window
    tree.name = "MyCustomTree"
    val result2 = contributor.collect(invocationData(dataContext))
    assertThat(result2).hasSize(1)
    assertThat(result2.single().body).contains("Tree: MyCustomTree")

    // Falls back to "Tree" when nothing is set
    tree.name = null
    val result3 = contributor.collect(invocationData(dataContext))
    assertThat(result3).hasSize(1)
    assertThat(result3.single().body).contains("Tree: Tree")
  }

  @Test
  fun accessibleNameTakesPrecedenceOverToolWindow() {
    val tree = createTree("Root", listOf("Item"))
    selectNodes(tree, listOf("Item"))
    tree.accessibleContext.accessibleName = "Changes tree"
    val toolWindow = java.lang.reflect.Proxy.newProxyInstance(
      ToolWindow::class.java.classLoader,
      arrayOf(ToolWindow::class.java),
    ) { _, method, _ -> if (method.name == "getId") "Version Control" else null } as ToolWindow
    val dataContext = testDataContext(
      PlatformCoreDataKeys.CONTEXT_COMPONENT to tree,
      PlatformDataKeys.TOOL_WINDOW to toolWindow,
    )

    val result = contributor.collect(invocationData(dataContext))
    assertThat(result).hasSize(1)
    assertThat(result.single().body).contains("Tree: Changes tree")
  }

  private fun createTree(rootName: String, children: List<String>): JTree {
    val root = DefaultMutableTreeNode(rootName)
    for (child in children) {
      root.add(DefaultMutableTreeNode(child))
    }
    return JTree(DefaultTreeModel(root))
  }

  private fun selectNodes(tree: JTree, nodeNames: List<String>) {
    val root = tree.model.root as DefaultMutableTreeNode
    val paths = nodeNames.mapNotNull { name ->
      val child = root.children().asSequence().firstOrNull { (it as DefaultMutableTreeNode).userObject.toString() == name }
      child?.let { TreePath(arrayOf(root, it)) }
    }.toTypedArray()
    timeoutRunBlocking(context = Dispatchers.EDT) { tree.selectionPaths = paths }
  }

  private fun testDataContext(vararg entries: Pair<DataKey<*>, Any>): DataContext {
    val map = entries.associate { (key, value) -> key.name to value }
    return DataContext { dataId ->
      @Suppress("UNCHECKED_CAST")
      map[dataId]
    }
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

  private fun invocationDataWithoutContext(): AgentPromptInvocationData {
    val project = ProjectManager.getInstance().defaultProject
    return AgentPromptInvocationData(
      project = project,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent",
      actionPlace = "MainMenu",
      invokedAtMs = 0L,
      attributes = emptyMap(),
    )
  }
}

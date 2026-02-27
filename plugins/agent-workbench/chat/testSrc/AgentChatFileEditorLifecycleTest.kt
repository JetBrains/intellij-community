// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class AgentChatFileEditorLifecycleTest {
  @Test
  fun preferredFocusedComponentDoesNotStartTerminalInitialization() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = testFile(),
      terminalTabs = terminalTabs,
    )

    val preferred = editor.preferredFocusedComponent

    assertThat(preferred).isSameAs(editor.component)
    assertThat(terminalTabs.createCalls).isEqualTo(0)
  }

  @Test
  fun selectNotifyInitializesTerminalOnce() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = testFile(),
      terminalTabs = terminalTabs,
    )

    editor.selectNotify()
    editor.selectNotify()

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(editor.preferredFocusedComponent).isSameAs(terminalTabs.tab.preferredFocusableComponent)
  }

  @Test
  fun disposeClosesInitializedTerminalTabOnce() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = testFile(),
      terminalTabs = terminalTabs,
    )

    editor.selectNotify()
    Disposer.dispose(editor)
    Disposer.dispose(editor)

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.closeCalls).isEqualTo(1)
  }

  @Test
  fun disposeWithoutInitializationDoesNotCloseTerminalTab() {
    val terminalTabs = FakeAgentChatTerminalTabs()
    val editor = AgentChatFileEditor(
      project = testProject(),
      file = testFile(),
      terminalTabs = terminalTabs,
    )

    Disposer.dispose(editor)

    assertThat(terminalTabs.createCalls).isEqualTo(0)
    assertThat(terminalTabs.closeCalls).isEqualTo(0)
  }
}

private class FakeAgentChatTerminalTabs : AgentChatTerminalTabs {
  var createCalls: Int = 0
  var closeCalls: Int = 0
  val tab = FakeAgentChatTerminalTab()

  override fun createTab(project: Project, file: AgentChatVirtualFile): AgentChatTerminalTab {
    createCalls++
    return tab
  }

  override fun closeTab(project: Project, tab: AgentChatTerminalTab) {
    closeCalls++
    (tab as? FakeAgentChatTerminalTab)?.coroutineScope?.cancel()
  }
}

private class FakeAgentChatTerminalTab : AgentChatTerminalTab {
  override val component: JComponent = JPanel()
  override val preferredFocusableComponent: JComponent = JButton("focus")
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext = Job()
  }
  override val keyEventsFlow: Flow<*> = emptyFlow<Unit>()
}

private fun testFile(): AgentChatVirtualFile {
  return AgentChatVirtualFile(
    projectPath = "/work/project-a",
    threadIdentity = "CODEX:thread-1",
    shellCommand = listOf("codex", "resume", "thread-1"),
    threadId = "thread-1",
    threadTitle = "Thread",
    subAgentId = null,
    projectHash = "hash-1",
  )
}

private fun testProject(): Project {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "isDisposed" -> false
      "toString" -> "Project(agent-chat-editor-lifecycle-test)"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> defaultValue(method.returnType)
    }
  }
  return Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java), handler) as Project
}

private fun defaultValue(returnType: Class<*>): Any? {
  return when {
    !returnType.isPrimitive -> null
    returnType == Boolean::class.javaPrimitiveType -> false
    returnType == Int::class.javaPrimitiveType -> 0
    returnType == Long::class.javaPrimitiveType -> 0L
    returnType == Short::class.javaPrimitiveType -> 0.toShort()
    returnType == Byte::class.javaPrimitiveType -> 0.toByte()
    returnType == Float::class.javaPrimitiveType -> 0f
    returnType == Double::class.javaPrimitiveType -> 0.0
    returnType == Char::class.javaPrimitiveType -> '\u0000'
    else -> null
  }
}

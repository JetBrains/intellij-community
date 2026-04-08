// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabBuilder
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class AgentChatTerminalTabBuilderConfigurationTest {
  @Test
  fun configurePassesSourceNavigationProjectPathToTerminalTabBuilder() {
    val builder = RecordingTerminalToolWindowTabBuilder()
    val file = AgentChatVirtualFile(
      projectPath = "/tmp/source-project",
      threadIdentity = "thread-1",
      shellCommand = listOf("ignored"),
      threadId = "thread-id",
      threadTitle = "Thread Title",
      subAgentId = null,
    )
    val launchSpec = AgentSessionTerminalLaunchSpec(
      command = listOf("agent", "resume"),
      envVariables = mapOf("A" to "B"),
    )

    val configuredBuilder = configureAgentChatTerminalTabBuilder(builder.proxy, file, launchSpec)

    assertThat(configuredBuilder).isSameAs(builder.proxy)
    assertThat(builder.shouldAddToToolWindow).isFalse()
    assertThat(builder.deferSessionStartUntilUiShown).isTrue()
    assertThat(builder.workingDirectory).isEqualTo("/tmp/source-project")
    assertThat(builder.sourceNavigationProjectPath).isEqualTo("/tmp/source-project")
    assertThat(builder.processType).isEqualTo(TerminalProcessType.NON_SHELL)
    assertThat(builder.tabName).isEqualTo("Thread Title")
    assertThat(builder.shellCommand).containsExactly("agent", "resume")
    assertThat(builder.envVariables).containsExactlyEntriesOf(mapOf("A" to "B"))
  }

  @Test
  fun configureSkipsBlankProjectPathForWorkingDirectoryAndSourceNavigation() {
    val builder = RecordingTerminalToolWindowTabBuilder()
    val file = AgentChatVirtualFile(
      projectPath = "   ",
      threadIdentity = "thread-1",
      shellCommand = listOf("ignored"),
      threadId = "thread-id",
      threadTitle = "Thread Title",
      subAgentId = null,
    )
    val launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("agent"))

    configureAgentChatTerminalTabBuilder(builder.proxy, file, launchSpec)

    assertThat(builder.workingDirectory).isNull()
    assertThat(builder.sourceNavigationProjectPath).isNull()
  }
}

private class RecordingTerminalToolWindowTabBuilder : InvocationHandler {
  val proxy: TerminalToolWindowTabBuilder = Proxy.newProxyInstance(
    TerminalToolWindowTabBuilder::class.java.classLoader,
    arrayOf(TerminalToolWindowTabBuilder::class.java),
    this,
  ) as TerminalToolWindowTabBuilder

  var shouldAddToToolWindow: Boolean? = null
  var deferSessionStartUntilUiShown: Boolean? = null
  var workingDirectory: String? = null
  var sourceNavigationProjectPath: String? = null
  var processType: TerminalProcessType? = null
  var tabName: String? = null
  var shellCommand: List<String>? = null
  var envVariables: Map<String, String>? = null

  override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
    return when (method.name) {
      "shouldAddToToolWindow" -> {
        shouldAddToToolWindow = args?.firstOrNull() as Boolean
        proxy
      }
      "deferSessionStartUntilUiShown" -> {
        deferSessionStartUntilUiShown = args?.firstOrNull() as Boolean
        proxy
      }
      "workingDirectory" -> {
        workingDirectory = args?.firstOrNull() as String?
        proxy
      }
      "sourceNavigationProjectPath" -> {
        sourceNavigationProjectPath = args?.firstOrNull() as String?
        proxy
      }
      "processType" -> {
        processType = args?.firstOrNull() as TerminalProcessType
        proxy
      }
      "tabName" -> {
        tabName = args?.firstOrNull() as String?
        proxy
      }
      "shellCommand" -> {
        @Suppress("UNCHECKED_CAST")
        shellCommand = args?.firstOrNull() as List<String>?
        proxy
      }
      "envVariables" -> {
        @Suppress("UNCHECKED_CAST")
        envVariables = args?.firstOrNull() as Map<String, String>?
        proxy
      }
      "createTab" -> builderProxyTerminalToolWindowTab()
      "toString" -> "RecordingTerminalToolWindowTabBuilder"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> builderDefaultValue(method.returnType)
    }
  }
}

private fun builderProxyTerminalToolWindowTab(): TerminalToolWindowTab {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "toString" -> "TerminalToolWindowTab(agent-chat-terminal-builder-test)"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> builderDefaultValue(method.returnType)
    }
  }
  return Proxy.newProxyInstance(
    TerminalToolWindowTab::class.java.classLoader,
    arrayOf(TerminalToolWindowTab::class.java),
    handler,
  ) as TerminalToolWindowTab
}

private fun builderDefaultValue(returnType: Class<*>): Any? {
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

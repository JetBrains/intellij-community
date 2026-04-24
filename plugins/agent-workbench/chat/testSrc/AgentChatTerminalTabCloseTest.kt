// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class AgentChatTerminalTabCloseTest {
  @Test
  fun closeUsesTabsManagerWhenTabContentHasManager() {
    var closeCalls = 0
    var releaseCalls = 0

    val contentManager = proxyContentManager()
    val content = proxyContent(
      manager = contentManager,
      onRelease = { releaseCalls++ },
    )
    val tab = proxyTerminalToolWindowTab(content)
    closeTerminalToolWindowTab(
      project = testProject(),
      tab = tab,
      managerProvider = {
        proxyTabsManager { closedTab ->
          closeCalls++
          assertThat(closedTab).isSameAs(tab)
        }
      },
    )

    assertThat(closeCalls).isEqualTo(1)
    assertThat(releaseCalls).isEqualTo(0)
  }

  @Test
  fun closeReleasesContentWhenTabIsDetached() {
    var releaseCalls = 0

    val content = proxyContent(
      manager = null,
      onRelease = { releaseCalls++ },
    )
    val tab = proxyTerminalToolWindowTab(content)
    closeTerminalToolWindowTab(
      project = testProject(),
      tab = tab,
      managerProvider = {
        error("Tabs manager should not be used for detached terminal tab content")
      },
    )

    assertThat(releaseCalls).isEqualTo(1)
  }
}

private fun proxyTerminalToolWindowTab(content: Content): TerminalToolWindowTab {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "getContent" -> content
      "toString" -> "TerminalToolWindowTab(agent-chat-test)"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> defaultValue(method.returnType)
    }
  }
  return Proxy.newProxyInstance(
    TerminalToolWindowTab::class.java.classLoader,
    arrayOf(TerminalToolWindowTab::class.java),
    handler,
  ) as TerminalToolWindowTab
}

private fun proxyContent(
  manager: ContentManager?,
  onRelease: () -> Unit,
): Content {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "getManager" -> manager
      "release" -> {
        onRelease()
        null
      }
      "toString" -> "Content(agent-chat-test)"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> defaultValue(method.returnType)
    }
  }
  return Proxy.newProxyInstance(
    Content::class.java.classLoader,
    arrayOf(Content::class.java),
    handler,
  ) as Content
}

private fun proxyContentManager(): ContentManager {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "toString" -> "ContentManager(agent-chat-test)"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> defaultValue(method.returnType)
    }
  }
  return Proxy.newProxyInstance(
    ContentManager::class.java.classLoader,
    arrayOf(ContentManager::class.java),
    handler,
  ) as ContentManager
}

private fun proxyTabsManager(
  onCloseTab: (TerminalToolWindowTab) -> Unit,
): TerminalToolWindowTabsManager {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "closeTab" -> {
        onCloseTab(args?.first() as TerminalToolWindowTab)
        null
      }
      "getTabs" -> emptyList<TerminalToolWindowTab>()
      "toString" -> "TerminalToolWindowTabsManager(agent-chat-test)"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> defaultValue(method.returnType)
    }
  }
  return Proxy.newProxyInstance(
    TerminalToolWindowTabsManager::class.java.classLoader,
    arrayOf(TerminalToolWindowTabsManager::class.java),
    handler,
  ) as TerminalToolWindowTabsManager
}

private fun testProject(): Project {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "isDisposed" -> false
      "toString" -> "Project(agent-chat-terminal-tab-close-test)"
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

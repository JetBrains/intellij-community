// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.openapi.project.Project
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptPalettePopupActivationDecisionsTest {
  @Test
  fun sameProjectFrameActivationRefocusesVisiblePopup() {
    val project = projectProxy("source-project")

    assertThat(
      shouldRefocusPromptOnFrameActivated(
        popupProject = project,
        activatedProject = project,
        isPopupVisible = true,
      )
    ).isTrue()
  }

  @Test
  fun hiddenPopupDoesNotRefocusOnFrameActivation() {
    val project = projectProxy("source-project")

    assertThat(
      shouldRefocusPromptOnFrameActivated(
        popupProject = project,
        activatedProject = project,
        isPopupVisible = false,
      )
    ).isFalse()
  }

  @Test
  fun activationFromDifferentProjectDoesNotRefocusPopup() {
    val project = projectProxy("source-project")
    val otherProject = projectProxy("other-project")

    assertThat(
      shouldRefocusPromptOnFrameActivated(
        popupProject = project,
        activatedProject = otherProject,
        isPopupVisible = true,
      )
    ).isFalse()
  }

  @Test
  fun activationWithoutProjectFrameDoesNotRefocusPopup() {
    val project = projectProxy("source-project")

    assertThat(
      shouldRefocusPromptOnFrameActivated(
        popupProject = project,
        activatedProject = null,
        isPopupVisible = true,
      )
    ).isFalse()
  }

  private fun projectProxy(name: String): Project {
    val handler = InvocationHandler { proxy, method, args ->
      when (method.name) {
        "getName" -> name
        "isOpen" -> true
        "isDisposed" -> false
        "toString" -> "MockProject($name)"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> null
      }
    }
    return Proxy.newProxyInstance(
      Project::class.java.classLoader,
      arrayOf(Project::class.java),
      handler,
    ) as Project
  }
}

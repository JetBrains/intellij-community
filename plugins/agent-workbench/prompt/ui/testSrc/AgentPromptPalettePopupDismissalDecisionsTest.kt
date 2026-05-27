// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.openapi.project.Project
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import javax.swing.JPanel

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptPalettePopupDismissalDecisionsTest {
  @Test
  fun sameFrameMouseClickAllowsPopupCancellation() {
    val project = projectProxy("source-project")
    val content = JPanel()
    assertThat(
      shouldAllowPromptPopupCancellation(
        popupProject = project,
        isRecentSourceFrameActivation = false,
        currentEvent = mousePressed(content),
        isExplicitClose = false,
        resolveProject = byComponent(content to project),
      )
    ).isTrue()
  }

  @Test
  fun activationClickInSourceFrameDoesNotAllowPopupCancellation() {
    // The click came right after WINDOW_ACTIVATED on the source frame (user was in another app or
    // another IDE frame and clicked back into the source frame). The click's purpose is window
    // activation, not interaction with the underlying component.
    val project = projectProxy("source-project")
    val content = JPanel()
    assertThat(
      shouldAllowPromptPopupCancellation(
        popupProject = project,
        isRecentSourceFrameActivation = true,
        currentEvent = mousePressed(content),
        isExplicitClose = false,
        resolveProject = byComponent(content to project),
      )
    ).isFalse()
  }

  @Test
  fun otherProjectMouseClickDoesNotAllowPopupCancellation() {
    val sourceProject = projectProxy("source-project")
    val otherProject = projectProxy("other-project")
    val otherContent = JPanel()
    assertThat(
      shouldAllowPromptPopupCancellation(
        popupProject = sourceProject,
        isRecentSourceFrameActivation = false,
        currentEvent = mousePressed(otherContent),
        isExplicitClose = false,
        resolveProject = byComponent(otherContent to otherProject),
      )
    ).isFalse()
  }

  @Test
  fun shortcutDrivenWindowSwitchDoesNotAllowPopupCancellation() {
    val sourceProject = projectProxy("source-project")
    val content = JPanel()
    assertThat(
      shouldAllowPromptPopupCancellation(
        popupProject = sourceProject,
        isRecentSourceFrameActivation = false,
        currentEvent = keyPressed(content, KeyEvent.VK_BACK_QUOTE, KeyEvent.META_DOWN_MASK),
        isExplicitClose = false,
        resolveProject = byComponent(content to sourceProject),
      )
    ).isFalse()
  }

  @Test
  fun escapeAllowsPopupCancellation() {
    val sourceProject = projectProxy("source-project")
    val content = JPanel()
    assertThat(
      shouldAllowPromptPopupCancellation(
        popupProject = sourceProject,
        isRecentSourceFrameActivation = false,
        currentEvent = keyPressed(content, KeyEvent.VK_ESCAPE, modifiersEx = 0),
        isExplicitClose = false,
        resolveProject = byComponent(content to sourceProject),
      )
    ).isTrue()
  }

  @Test
  fun explicitCloseAllowsPopupCancellation() {
    val sourceProject = projectProxy("source-project")
    val otherProject = projectProxy("other-project")
    val otherContent = JPanel()
    // Submit via a click on the Send button runs while trueCurrentEvent is a MouseEvent that would
    // otherwise be blocked because it targets a different component; the flag must override that.
    assertThat(
      shouldAllowPromptPopupCancellation(
        popupProject = sourceProject,
        isRecentSourceFrameActivation = false,
        currentEvent = mousePressed(otherContent),
        isExplicitClose = true,
        resolveProject = byComponent(otherContent to otherProject),
      )
    ).isTrue()
  }

  @Test
  fun unclassifiedProgrammaticCloseIsAllowed() {
    val sourceProject = projectProxy("source-project")
    assertThat(
      shouldAllowPromptPopupCancellation(
        popupProject = sourceProject,
        isRecentSourceFrameActivation = false,
        currentEvent = null,
        isExplicitClose = false,
        resolveProject = { _ -> null },
      )
    ).isTrue()
  }

  @Test
  fun unresolvedPopupOriginAllowsPopupCancellation() {
    val otherContent = JPanel()
    assertThat(
      shouldAllowPromptPopupCancellation(
        popupProject = null,
        isRecentSourceFrameActivation = false,
        currentEvent = mousePressed(otherContent),
        isExplicitClose = false,
        resolveProject = { _ -> null },
      )
    ).isTrue()
  }

  private fun mousePressed(source: Component): MouseEvent {
    return MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0L, 0, 0, 0, 1, false)
  }

  private fun keyPressed(source: Component, keyCode: Int, modifiersEx: Int): KeyEvent {
    return KeyEvent(source, KeyEvent.KEY_PRESSED, 0L, modifiersEx, keyCode, KeyEvent.CHAR_UNDEFINED)
  }

  private fun byComponent(vararg pairs: Pair<Component, Project>): (Component?) -> Project? {
    val map = pairs.toMap()
    return { component -> component?.let(map::get) }
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

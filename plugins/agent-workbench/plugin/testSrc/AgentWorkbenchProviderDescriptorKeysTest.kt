// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.plugin

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

/**
 * Pins the bundle keys that built-in providers expose for the New Thread UI.
 * Catches silent regressions where a future descriptor edit changes which key is referenced.
 * Only descriptor-level constants are pinned here; resolved bundle messages are exercised by the
 * sessions-actions tests that already build action presentations from the same keys.
 */
@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchProviderDescriptorKeysTest {
  @Test
  fun codexExposesGenericQuickStartKeys() {
    val descriptor = descriptorFor(AgentSessionProvider.CODEX)
    assertEquals("toolwindow.action.new.session.codex", descriptor.newSessionLabelKey)
    assertEquals("toolwindow.action.new.session.codex", descriptor.quickStartLabelKey)
    assertEquals("action.AgentWorkbenchSessions.NewThreadQuick.text", descriptor.quickStartActionTextKey)
    assertEquals("action.AgentWorkbenchSessions.NewThreadQuick.description", descriptor.quickStartActionDescriptionKey)
    assertNull(descriptor.quickStartActionTargetDescriptionKey)
    assertEquals("toolwindow.action.new.thread", descriptor.newSessionTitleKey)
  }

  @Test
  fun claudeExposesGenericQuickStartKeys() {
    val descriptor = descriptorFor(AgentSessionProvider.CLAUDE)
    assertEquals("toolwindow.action.new.session.claude", descriptor.newSessionLabelKey)
    assertEquals("toolwindow.action.new.session.claude", descriptor.quickStartLabelKey)
    assertEquals("action.AgentWorkbenchSessions.NewThreadQuick.text", descriptor.quickStartActionTextKey)
    assertEquals("action.AgentWorkbenchSessions.NewThreadQuick.description", descriptor.quickStartActionDescriptionKey)
    assertNull(descriptor.quickStartActionTargetDescriptionKey)
    assertEquals("toolwindow.action.new.thread", descriptor.newSessionTitleKey)
  }

  @Test
  fun junieExposesGenericQuickStartKeys() {
    val descriptor = descriptorFor(AgentSessionProvider.JUNIE)
    assertEquals("toolwindow.action.new.session.junie", descriptor.newSessionLabelKey)
    assertEquals("toolwindow.action.new.session.junie", descriptor.quickStartLabelKey)
    assertEquals("action.AgentWorkbenchSessions.NewThreadQuick.text", descriptor.quickStartActionTextKey)
    assertEquals("action.AgentWorkbenchSessions.NewThreadQuick.description", descriptor.quickStartActionDescriptionKey)
    assertNull(descriptor.quickStartActionTargetDescriptionKey)
    assertEquals("toolwindow.action.new.thread", descriptor.newSessionTitleKey)
  }

  @Test
  fun terminalExposesTerminalSpecificQuickStartKeys() {
    val descriptor = descriptorFor(AgentSessionProvider.TERMINAL)
    assertEquals("toolwindow.action.new.session.terminal", descriptor.newSessionLabelKey)
    assertEquals("action.AgentWorkbenchSessions.NewTerminalSessionQuick.text", descriptor.quickStartActionTextKey)
    assertEquals("action.AgentWorkbenchSessions.NewTerminalSessionQuick.description", descriptor.quickStartActionDescriptionKey)
    assertEquals("action.AgentWorkbenchSessions.NewTerminalSessionQuick.target.description", descriptor.quickStartActionTargetDescriptionKey)
    assertEquals("toolwindow.action.new.session.terminal.title", descriptor.newSessionTitleKey)
  }

  private fun descriptorFor(provider: AgentSessionProvider): AgentSessionProviderDescriptor {
    val descriptor = AgentSessionProviders.find(provider)
    assertNotNull(descriptor) { "Provider ${provider.value} not registered" }
    return descriptor!!
  }
}

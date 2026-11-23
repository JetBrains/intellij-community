// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginSet
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

@TestApplication
internal class ImplementationDetailPluginEnablementTest {
  @TempDir
  lateinit var tempDir: Path

  private lateinit var originalPluginSet: PluginSet
  private lateinit var session: PluginManagerSession

  private val hostId = PluginId.getId("host")
  private val detailId = PluginId.getId("detail")

  @BeforeEach
  fun setUp() {
    originalPluginSet = PluginManagerCore.getPluginSet()
    plugin("host") { name = "Host" }.installAt(tempDir)
    plugin("detail") {
      name = "Detail"
      implementationDetail = true
      depends("host")
    }.installAt(tempDir)
    PluginManagerCore.setPluginSet(PluginSetTestBuilder.fromPath(tempDir).build(configureClassLoaders = false))
    session = PluginManagerSessionService.getInstance().createSession(UUID.randomUUID().toString())
    session.pluginStates[hostId] = PluginEnabledState.ENABLED
    session.pluginStates[detailId] = PluginEnabledState.ENABLED
  }

  @AfterEach
  fun tearDown() {
    PluginManagerSessionService.getInstance().removeSession(session.sessionId)
    PluginManagerCore.setPluginSet(originalPluginSet)
  }

  @Test
  fun `implementation detail plugin allows disable`() {
    assertThat(PluginManagerCore.isDisableAllowed(detail())).isTrue()
  }

  @Test
  fun `apply disables implementation detail plugin that the user disables`() {
    val result = DefaultUiPluginManagerController.enablePlugins(session.sessionId, listOf(detailId), false, null)

    assertThat(result.changedStates).containsEntry(detailId, false)
    assertThat(DefaultUiPluginManagerController.isImplicitImplementationDetailDisable(session, detail(), PluginEnabledState.DISABLED))
      .isFalse()
  }

  @Test
  fun `apply keeps implementation detail plugin that a dependency change disables`() {
    val result = DefaultUiPluginManagerController.enablePlugins(session.sessionId, listOf(hostId), false, null)

    assertThat(result.changedStates).containsEntry(hostId, false).containsEntry(detailId, false)
    assertThat(DefaultUiPluginManagerController.isImplicitImplementationDetailDisable(session, detail(), PluginEnabledState.DISABLED))
      .isTrue()
  }

  @Test
  fun `enable of implementation detail plugin cancels its direct disable`() {
    DefaultUiPluginManagerController.enablePlugins(session.sessionId, listOf(detailId), false, null)
    DefaultUiPluginManagerController.enablePlugins(session.sessionId, listOf(detailId), true, null)
    DefaultUiPluginManagerController.enablePlugins(session.sessionId, listOf(hostId), false, null)

    assertThat(DefaultUiPluginManagerController.isImplicitImplementationDetailDisable(session, detail(), PluginEnabledState.DISABLED))
      .isTrue()
  }

  private fun detail() = checkNotNull(PluginManagerCore.getPluginSet().findInstalledPlugin(detailId))
}

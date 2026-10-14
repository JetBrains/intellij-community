// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.newui.DefaultUiPluginManagerController
import com.intellij.ide.plugins.newui.PluginManagerSession
import com.intellij.ide.plugins.newui.PluginManagerSessionService
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

@TestApplication
internal class PluginLoadingProblemControllerTest {
  @TempDir
  lateinit var tempDir: Path

  private lateinit var originalPluginSet: PluginSet
  private lateinit var session: PluginManagerSession

  @BeforeEach
  fun createSession() {
    originalPluginSet = PluginManagerCore.getPluginSet()
    session = PluginManagerSessionService.getInstance().createSession(UUID.randomUUID().toString())
  }

  @AfterEach
  fun restoreState() {
    PluginManagerSessionService.getInstance().removeSession(session.sessionId)
    PluginManagerCore.setPluginSet(originalPluginSet)
  }

  @Test
  fun `disabled dependency offers an Enable suggestion`(): Unit = timeoutRunBlocking {
    installDisabledDependency()

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.suggestToEnableRequiredPlugins).isTrue()
    assertThat(result.requiredPluginNames).containsExactly("Root")
    assertThat(result.loadingError).isNull()
  }

  @Test
  fun `a transitive disabled dependency offers an Enable suggestion`(): Unit = timeoutRunBlocking {
    installDisabledDependency(transitive = true)

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.suggestToEnableRequiredPlugins).isTrue()
    assertThat(result.requiredPluginNames).containsExactly("Root")
    assertThat(result.loadingError).isNull()
  }

  @Test
  fun `a disabled affected plugin suppresses loading problems`(): Unit = checkDisabledAffectedPlugin(transitive = false)

  @Test
  fun `a disabled affected plugin suppresses transitive loading problems`(): Unit = checkDisabledAffectedPlugin(transitive = true)

  @Test
  fun `resolved session dependencies suppress the Enable suggestion`(): Unit = checkResolvedSessionDependencies(transitive = false)

  @Test
  fun `resolved transitive session dependencies suppress the Enable suggestion`(): Unit = checkResolvedSessionDependencies(transitive = true)

  @Test
  fun `disabled Ultimate still produces a generic controller Enable suggestion`(): Unit = timeoutRunBlocking {
    installDisabledDependency(rootId = PluginManagerCore.ULTIMATE_PLUGIN_ID.idString)

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.suggestToEnableRequiredPlugins).isTrue()
    assertThat(result.requiredPluginNames).containsExactly("Root")
    assertThat(result.loadingError).isNull()
  }

  @Test
  fun `transitive disabled Ultimate still produces a generic controller Enable suggestion`(): Unit = timeoutRunBlocking {
    installDisabledDependency(transitive = true, rootId = PluginManagerCore.ULTIMATE_PLUGIN_ID.idString)

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.suggestToEnableRequiredPlugins).isTrue()
    assertThat(result.requiredPluginNames).containsExactly("Root")
    assertThat(result.loadingError).isNull()
  }

  private fun checkDisabledAffectedPlugin(transitive: Boolean): Unit = timeoutRunBlocking {
    installDisabledDependency(transitive)
    session.pluginStates[PluginId.getId("affected")] = PluginEnabledState.DISABLED

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.suggestToEnableRequiredPlugins).isFalse()
    assertThat(result.requiredPluginNames).isEmpty()
    assertThat(result.loadingError).isNull()
  }

  private fun checkResolvedSessionDependencies(transitive: Boolean): Unit = timeoutRunBlocking {
    installDisabledDependency(transitive)
    session.pluginStates[PluginId.getId("root")] = PluginEnabledState.ENABLED
    DefaultUiPluginManagerController.updatePluginDependenciesSync(session.sessionId)

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.suggestToEnableRequiredPlugins).isFalse()
    assertThat(result.requiredPluginNames).isEmpty()
    assertThat(result.loadingError).isNull()
  }

  private fun installDisabledDependency(transitive: Boolean = false, rootId: String = "root") {
    plugin(rootId) { name = "Root" }.installAt(tempDir)
    if (transitive) {
      plugin("intermediate") {
        name = "Intermediate"
        depends(rootId)
      }.installAt(tempDir)
      session.pluginStates[PluginId.getId("intermediate")] = PluginEnabledState.ENABLED
    }
    plugin("affected") {
      name = "Affected"
      depends(if (transitive) "intermediate" else rootId)
    }.installAt(tempDir)
    val pluginSet = PluginSetTestBuilder.fromPath(tempDir)
      .withDisabledPlugins(rootId)
      .build(configureClassLoaders = false)
    PluginManagerCore.setPluginSet(pluginSet)
    session.pluginStates[PluginId.getId(rootId)] = PluginEnabledState.DISABLED
    session.pluginStates[PluginId.getId("affected")] = PluginEnabledState.ENABLED
    DefaultUiPluginManagerController.updatePluginDependenciesSync(session.sessionId)
  }
}

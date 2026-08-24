// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.idea.TestFor
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.PluginSpec
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

@TestApplication
internal class PluginManagerStateServiceTest {
  private val service = PluginManagerStateService()

  @TempDir
  lateinit var tempDir: Path

  private lateinit var originalPluginSet: PluginSet

  @BeforeEach
  fun rememberOriginalPluginSet() {
    originalPluginSet = PluginManagerCore.getPluginSet()
  }

  @AfterEach
  fun restoreOriginalPluginSet() {
    PluginManagerCore.setPluginSet(originalPluginSet)
  }

  @Test
  @TestFor(issues = ["IJPL-183884"])
  fun `loading error is cleared when disabled dependency and dependent are loaded`() {
    val dependency = plugin("bar") { name = "Bar" }
    val dependent = plugin("foo") {
      name = "Foo"
      depends("bar")
    }
    install(dependency, dependent)

    val initialPluginSet = buildPluginSet(disabledPluginIds = arrayOf("bar"))
    val initialState = publishAndGetState(initialPluginSet)
    val dependentId = PluginId.getId("foo")
    val dependencyId = PluginId.getId("bar")
    val reason = initialState.pluginNonLoadReasons[dependentId]

    assertThat(reason).isInstanceOf(PluginDependencyIsDisabled::class.java)
    reason as PluginDependencyIsDisabled
    assertThat(reason.dependencyId).isEqualTo(dependencyId)
    assertThat(initialState.loadingErrors).containsExactly(reason.detailedMessage)
    assertThat(initialState.pluginsToEnable).containsExactlyEntriesOf(mapOf(dependencyId to "Bar"))
    assertThat(initialState.pluginsToDisable).containsExactlyEntriesOf(mapOf(dependentId to "Foo"))

    val loadedPluginSet = buildPluginSet()
    val loadedState = publishAndGetState(loadedPluginSet)

    assertThat(loadedState).isNotSameAs(initialState)
    assertThat(loadedPluginSet.enabledPlugins.map { it.pluginId }).contains(dependencyId, dependentId)
    assertThat(loadedState.pluginNonLoadReasons).isEmpty()
    assertThat(loadedState.loadingErrors).isEmpty()
    assertThat(loadedState.pluginsToEnable).isEmpty()
    assertThat(loadedState.pluginsToDisable).isEmpty()
  }

  @Test
  fun `snapshot is cached for the same plugin set and reporting policy`() {
    install(plugin("foo") {
      name = "Foo"
      depends("missing")
    })
    PluginManagerCore.setPluginSet(buildPluginSet())

    val first = service.getCurrentState(USER_REPORTING_POLICY)
    val second = service.getCurrentState(USER_REPORTING_POLICY)

    assertThat(second).isSameAs(first)
  }

  @Test
  fun `reporting policy only suppresses notification messages`() {
    install(plugin("foo") {
      name = "Foo"
      depends("missing")
    })
    PluginManagerCore.setPluginSet(buildPluginSet())

    val visibleState = checkNotNull(service.getCurrentState(USER_REPORTING_POLICY))
    val hiddenState = checkNotNull(service.getCurrentState(SILENT_REPORTING_POLICY))

    assertThat(visibleState.loadingErrors).isNotEmpty()
    assertThat(hiddenState).isNotSameAs(visibleState)
    assertThat(hiddenState.loadingErrors).isEmpty()
    assertThat(reasonTypes(hiddenState)).isEqualTo(reasonTypes(visibleState))
    assertThat(reasonMessages(hiddenState)).isEqualTo(reasonMessages(visibleState))
    assertThat(hiddenState.pluginsToEnable).isEqualTo(visibleState.pluginsToEnable)
    assertThat(hiddenState.pluginsToDisable).isEqualTo(visibleState.pluginsToDisable)
  }

  @Test
  fun `missing dependency is exposed as a reason and disable suggestion`() {
    install(plugin("foo") {
      name = "Foo"
      depends("missing")
    })

    val state = publishAndGetState(buildPluginSet())
    val pluginId = PluginId.getId("foo")
    val reason = state.pluginNonLoadReasons[pluginId]

    assertThat(reason).isInstanceOf(PluginDependencyIsNotInstalled::class.java)
    reason as PluginDependencyIsNotInstalled
    assertThat(reason.dependencyNameOrId).isEqualTo("missing")
    assertThat(reason.shouldNotifyUser).isTrue()
    assertThat(reason.shortMessage).isNotBlank()
    assertThat(state.loadingErrors).containsExactly(reason.detailedMessage)
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(pluginId to "Foo"))
    assertThat(service.getPluginNonLoadReason(pluginId)).isSameAs(reason)
  }

  @Test
  fun `implementation detail errors are retained but not reported to the user`() {
    install(plugin("foo") {
      name = "Foo"
      implementationDetail = true
      depends("missing")
    })

    val state = publishAndGetState(buildPluginSet())
    val pluginId = PluginId.getId("foo")
    val reason = state.pluginNonLoadReasons[pluginId]

    assertThat(reason).isInstanceOf(PluginDependencyIsNotInstalled::class.java)
    assertThat(reason!!.shouldNotifyUser).isFalse()
    assertThat(state.loadingErrors).isEmpty()
    assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(pluginId to "Foo"))
  }

  @Test
  fun `explicitly disabled plugin has no loading error or suggested action`() {
    install(plugin("foo") { name = "Foo" })

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf("foo")))

    assertThat(state.pluginNonLoadReasons).isEmpty()
    assertThat(state.loadingErrors).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  private fun install(vararg plugins: PluginSpec) {
    plugins.forEach { it.installAt(tempDir) }
  }

  private fun buildPluginSet(disabledPluginIds: Array<String> = emptyArray()): PluginSet {
    return PluginSetTestBuilder.fromPath(tempDir)
      .withDisabledPlugins(*disabledPluginIds)
      .build(configureClassLoaders = false)
  }

  private fun publishAndGetState(pluginSet: PluginSet): PluginManagerStateSnapshot {
    PluginManagerCore.setPluginSet(pluginSet)
    return checkNotNull(service.getCurrentState(USER_REPORTING_POLICY))
  }

  private fun reasonTypes(state: PluginManagerStateSnapshot): Map<PluginId, Class<out PluginNonLoadReason>> =
    state.pluginNonLoadReasons.mapValues { it.value.javaClass }

  private fun reasonMessages(state: PluginManagerStateSnapshot): Map<PluginId, Pair<String, String>> =
    state.pluginNonLoadReasons.mapValues { it.value.shortMessage to it.value.detailedMessage }

  private companion object {
    val USER_REPORTING_POLICY = PluginLoadingErrorReportingPolicy(PluginLoadingErrorLogLevel.INFO, reportToUser = true)
    val SILENT_REPORTING_POLICY = USER_REPORTING_POLICY.copy(reportToUser = false)
  }
}

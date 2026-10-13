// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.idea.TestFor
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleVisibilityValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.PluginSpec
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
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

    assertThat(reason!!.disabledDependencyId).isEqualTo(dependencyId)
    assertThat(reason.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", dependencyId))
    assertThat(initialState.pluginLoadingProblemsForNotification).containsExactly(reason.detailedMessage)
    assertThat(initialState.pluginsToEnable).containsExactlyEntriesOf(mapOf(dependencyId to "Bar"))
    assertThat(initialState.pluginsToDisable).containsExactlyEntriesOf(mapOf(dependentId to "Foo"))

    val loadedPluginSet = buildPluginSet()
    val loadedState = publishAndGetState(loadedPluginSet)

    assertThat(loadedState).isNotSameAs(initialState)
    assertThat(loadedPluginSet.enabledPlugins.map { it.pluginId }).contains(dependencyId, dependentId)
    assertThat(loadedState.pluginNonLoadReasons).isEmpty()
    assertThat(loadedState.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(loadedState.pluginsToEnable).isEmpty()
    assertThat(loadedState.pluginsToDisable).isEmpty()
  }

  @Test
  @TestFor(issues = ["IJPL-246315", "IDEA-392708"])
  fun `transitive disabled dependency reports the disabled root`() {
    install(
      plugin("root") { name = "Root" },
      plugin("intermediate") {
        name = "Intermediate"
        depends("root")
      },
      plugin("affected") {
        name = "Affected"
        depends("intermediate")
      },
    )

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf("root")))
    val rootId = PluginId.getId("root")
    val intermediateId = PluginId.getId("intermediate")
    val affectedId = PluginId.getId("affected")
    val affectedReason = state.pluginNonLoadReasons[affectedId]
    val intermediateReason = state.pluginNonLoadReasons[intermediateId]

    assertThat(affectedReason!!.disabledDependencyId).isEqualTo(rootId)
    assertThat(intermediateReason!!.disabledDependencyId).isEqualTo(rootId)
    assertThat(affectedReason.shortMessage)
      .isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", rootId))
    assertThat(intermediateReason.shortMessage)
      .isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", rootId))
    assertThat(state.pluginLoadingProblemsForNotification).containsExactlyInAnyOrder(
      CoreBundle.message("plugin.loading.error.long.depends.on.disabled.plugin", "Affected", rootId),
      CoreBundle.message("plugin.loading.error.long.depends.on.disabled.plugin", "Intermediate", rootId),
    )
    assertThat(state.pluginsToEnable).containsExactlyEntriesOf(mapOf(rootId to "Root"))
    assertThat(state.pluginsToDisable).containsExactlyInAnyOrderEntriesOf(mapOf(affectedId to "Affected", intermediateId to "Intermediate"))

    val loadedPluginSet = buildPluginSet()
    val loadedState = publishAndGetState(loadedPluginSet)

    assertThat(loadedPluginSet.enabledPlugins.map { it.pluginId }).contains(rootId, intermediateId, affectedId)
    assertThat(loadedState.pluginNonLoadReasons).isEmpty()
    assertThat(loadedState.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(loadedState.pluginsToEnable).isEmpty()
    assertThat(loadedState.pluginsToDisable).isEmpty()
  }

  @Test
  fun `several intermediate plugins report one disabled root`() {
    install(plugin("root") { name = "Root" })
    for (index in 0..2) {
      install(plugin("dependent$index") {
        name = "Dependent $index"
        depends(if (index == 0) "root" else "dependent${index - 1}")
      })
    }

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf("root")))
    val rootId = PluginId.getId("root")

    assertThat(state.pluginNonLoadReasons).hasSize(3)
    for (problem in state.pluginNonLoadReasons.values) {
      assertThat(problem.disabledDependencyId).isEqualTo(rootId)
      assertThat(problem.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", rootId))
    }
    assertThat(state.pluginLoadingProblemsForNotification).containsExactlyInAnyOrderElementsOf(
      (0..2).map { CoreBundle.message("plugin.loading.error.long.depends.on.disabled.plugin", "Dependent $it", rootId) },
    )
    assertThat(state.pluginsToEnable).containsExactlyEntriesOf(mapOf(rootId to "Root"))
  }

  @Test
  fun `a required content module reports the transitive disabled root`() {
    install(
      plugin("root") { name = "Root" },
      plugin("intermediate") {
        name = "Intermediate"
        depends("root")
      },
      plugin("affected") {
        name = "Affected"
        content {
          module("affected.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            dependencies { plugin("intermediate") }
          }
        }
      },
    )

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf("root")))
    val rootId = PluginId.getId("root")
    val reason = state.pluginNonLoadReasons[PluginId.getId("affected")]

    assertThat(reason!!.disabledDependencyId).isEqualTo(rootId)
    assertThat(reason.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", rootId))
    assertThat(state.pluginsToEnable).containsExactlyEntriesOf(mapOf(rootId to "Root"))
  }

  @Test
  @TestFor(issues = ["IJPL-246315"])
  fun `a core content module reports the disabled root instead of IDEA CORE`() {
    install(
      plugin("root") { name = "Root" },
      plugin(PluginManagerCore.CORE_PLUGIN_ID) {
        name = "IDEA CORE"
        content(namespace = "jetbrains") {
          module("core.bridge") {
            moduleVisibility = ModuleVisibilityValue.PUBLIC
            dependencies { plugin("root") }
          }
        }
      },
      plugin("affected") {
        name = "Affected"
        dependencies { module("core.bridge") }
      },
    )

    val pluginSet = buildPluginSet(disabledPluginIds = arrayOf("root"))
    val state = publishAndGetState(pluginSet)
    val rootId = PluginId.getId("root")
    val affectedId = PluginId.getId("affected")
    val reason = state.pluginNonLoadReasons[affectedId]

    assertThat(pluginSet.enabledPlugins.map { it.pluginId }).contains(PluginManagerCore.CORE_ID)
    assertThat(pluginSet.resolvedPluginSet.getExclusionReason(pluginSet.allPlugins.single { it.pluginId == rootId }))
      .isInstanceOf(PluginIsMarkedDisabled::class.java)
    assertThat(state.pluginNonLoadReasons).containsOnlyKeys(affectedId)
    assertThat(reason!!.disabledDependencyId).isEqualTo(rootId)
    assertThat(reason.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", rootId))
    assertThat(state.pluginLoadingProblemsForNotification)
      .containsExactly(CoreBundle.message("plugin.loading.error.long.depends.on.disabled.plugin", "Affected", rootId))
    assertThat(state.pluginsToEnable).containsExactlyEntriesOf(mapOf(rootId to "Root"))
    assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(affectedId to "Affected"))
  }

  @Test
  fun `a root that is not disabled keeps the failed intermediate explanation`() {
    install(
      plugin("root") {
        name = "Root"
        depends("missing")
      },
      plugin("intermediate") {
        name = "Intermediate"
        depends("root")
      },
      plugin("affected") {
        name = "Affected"
        depends("intermediate")
      },
    )

    val state = publishAndGetState(buildPluginSet())
    val reason = state.pluginNonLoadReasons[PluginId.getId("affected")]

    assertThat(reason!!.disabledDependencyId).isNull()
    assertThat(reason.shortMessage)
      .isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.failed.to.load.plugin", "Intermediate"))
    assertThat(state.pluginsToEnable).isEmpty()
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

    assertThat(visibleState.pluginLoadingProblemsForNotification).isNotEmpty()
    assertThat(hiddenState).isNotSameAs(visibleState)
    assertThat(hiddenState.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(hiddenState.pluginNonLoadReasons.keys).isEqualTo(visibleState.pluginNonLoadReasons.keys)
    assertThat(hiddenState.pluginNonLoadReasons.mapValues { it.value.disabledDependencyId })
      .isEqualTo(visibleState.pluginNonLoadReasons.mapValues { it.value.disabledDependencyId })
    assertThat(hiddenState.pluginNonLoadReasons.mapValues { it.value.shouldNotifyUser })
      .isEqualTo(visibleState.pluginNonLoadReasons.mapValues { it.value.shouldNotifyUser })
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

    assertThat(reason!!.disabledDependencyId).isNull()
    assertThat(reason.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.not.installed.plugin", "missing"))
    assertThat(reason.shouldNotifyUser).isTrue()
    assertThat(state.pluginLoadingProblemsForNotification).containsExactly(reason.detailedMessage)
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

    assertThat(reason!!.shouldNotifyUser).isFalse()
    assertThat(reason.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.not.installed.plugin", "missing"))
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(pluginId to "Foo"))
  }

  @Test
  fun `explicitly disabled plugin has no loading error or suggested action`() {
    install(plugin("foo") { name = "Foo" })

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf("foo")))

    assertThat(state.pluginNonLoadReasons).isEmpty()
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
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

  private fun reasonMessages(state: PluginManagerStateSnapshot): Map<PluginId, Pair<String, String>> =
    state.pluginNonLoadReasons.mapValues { it.value.shortMessage to it.value.detailedMessage }

  private companion object {
    val USER_REPORTING_POLICY = PluginLoadingErrorReportingPolicy(PluginLoadingErrorLogLevel.INFO, reportToUser = true)
    val SILENT_REPORTING_POLICY = USER_REPORTING_POLICY.copy(reportToUser = false)
  }
}

package com.intellij.settingsSync.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.settingsSync.core.plugins.PluginManagerProxy
import com.intellij.settingsSync.core.plugins.SettingsSyncPluginManager
import com.intellij.settingsSync.core.plugins.SettingsSyncPluginsState
import com.intellij.settingsSync.core.plugins.SettingsSyncPluginsState.PluginData
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.util.containers.mapSmartSet
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach

@TestApplication
abstract class BasePluginManagerTest {
  internal lateinit var pluginManager: SettingsSyncPluginManager
  internal lateinit var testPluginManager: TestPluginManager

  @TestDisposable
  internal lateinit var testRootDisposable: Disposable
  protected lateinit var testScope: TestScope

  //is used in TestPluginDescriptor.Companion.allDependenciesOnly
  internal val modulesPlatform = TestPluginDescriptor(
    "com.intellij.modules.platform",
    bundled = true,
    essential = true,
    isDependencyOnly = true
  )
  //is used in TestPluginDescriptor.Companion.allDependenciesOnly
  internal val modulesLang = TestPluginDescriptor(
    "com.intellij.modules.lang",
    bundled = true,
    essential = true,
    isDependencyOnly = true
  )

  internal val modulesJava = TestPluginDescriptor(
    "com.intellij.modules.java",
    bundled = true,
    essential = true,
    isDependencyOnly = true
  )

  internal val quickJump = TestPluginDescriptor(
    "QuickJump",
    listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false))
  )
  internal val typengo = TestPluginDescriptor(
    "codeflections.typengo",
    listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false))
  )
  internal val ideaLight = TestPluginDescriptor(
    "color.scheme.IdeaLight",
    listOf(TestPluginDependency("com.intellij.modules.lang", isOptional = false))
  )
  internal val git4idea = TestPluginDescriptor(
    "git4idea",
    listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false)),
    bundled = true
  )
  internal val cvsOutdated = TestPluginDescriptor(
    "cvs",
    listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false)),
    bundled = false,
    compatible = false
  )
  internal val javascript = TestPluginDescriptor(
    "JavaScript",
    listOf(TestPluginDependency("css", false)),
    bundled = true,
    essential = true
  )
  internal val css = TestPluginDescriptor(
    "css",
    listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false)),
    bundled = true
  )
  internal val scala = TestPluginDescriptor(
    "org.intellij.scala",
    listOf(TestPluginDependency("com.intellij.modules.java", isOptional = false)),
    isDynamic = false
  )

  @BeforeEach
  fun setUp() {
    SettingsSyncSettings.getInstance().syncEnabled = true
    SettingsSyncSettings.getInstance().loadState(SettingsSyncSettings.State())
    testScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    testPluginManager = TestPluginManager(testScope)
    testPluginManager.addPluginDescriptors(*TestPluginDescriptor.allDependenciesOnly().toTypedArray())
    ApplicationManager.getApplication().replaceService(PluginManagerProxy::class.java, testPluginManager, testRootDisposable)
    pluginManager = SettingsSyncPluginManager(testScope)
    Disposer.register(testRootDisposable, pluginManager)
  }

  internal fun assertPluginManagerState(build: StateBuilder.() -> Unit) {
    assertPluginManagerState(state(build))
  }

  internal fun getPluginManagerState(): Map<PluginId, PluginData> {
    return pluginManager.state.plugins
  }

  internal fun assertPluginManagerState(expectedState: SettingsSyncPluginsState) {
    assertPluginsState(expectedState.plugins, getPluginManagerState())
  }

  internal fun assertIdeState(build: StateBuilder.() -> Unit) {
    assertIdeState(state(build))
  }

  internal fun getIdeState(): Map<PluginId, PluginData> {
    return PluginManagerProxy.getInstance().getPlugins().filter { !(it as TestPluginDescriptor).isDependencyOnly}.associate { plugin ->
      plugin.pluginId to PluginData(plugin.isEnabled)
    }
  }

  internal fun assertIdeState(expectedState: SettingsSyncPluginsState) {
    assertPluginsState(expectedState.plugins, getIdeState())
  }
}

internal fun state(build: StateBuilder.() -> Unit): SettingsSyncPluginsState {
  val builder = StateBuilder()
  builder.build()
  return SettingsSyncPluginsState(builder.states)
}

internal class StateBuilder {
  val states = mutableMapOf<PluginId, PluginData>()

  operator fun TestPluginDescriptor.invoke(
    enabled: Boolean,
    category: SettingsCategory = SettingsCategory.PLUGINS): Pair<PluginId, PluginData> {

    val pluginData = PluginData(enabled, category, this.pluginDependencies.mapSmartSet { it.pluginId.idString })
    states[pluginId] = pluginData
    return this.pluginId to pluginData
  }
}

internal fun assertPluginsState(expectedStates: Map<PluginId, PluginData>, actualStates: Map<PluginId, PluginData>) {
  fun stringifyStates(states: Map<PluginId, PluginData>) =
    states.entries
      .sortedBy { it.key }
      .joinToString { (id, data) -> "$id: ${enabledOrDisabled(data.enabled)}" }

  if (expectedStates.size != actualStates.size) {
    assertEquals(stringifyStates(expectedStates), stringifyStates(actualStates),
                 "Expected and actual states have different number of elements"
    )
  }
  for ((expectedId, expectedData) in expectedStates) {
    val actualData = actualStates[expectedId]
    assertNotNull(actualData, "Record for plugin $expectedId not found")
    assertEquals(expectedData.enabled, actualData!!.enabled, "Plugin $expectedId has incorrect state")
  }
}

private fun enabledOrDisabled(value: Boolean?) = if (value == null) "null" else if (value) "enabled" else "disabled"

package com.intellij.settingsSync.core

import com.intellij.openapi.extensions.PluginId
import com.intellij.settingsSync.core.plugins.SettingsSyncPluginsState
import com.intellij.settingsSync.core.plugins.SettingsSyncPluginsStateMerger.mergePluginStates
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MergePluginsStateTest {

  @Test
  fun `test merge plugins state`() {
    val baseState = pluginStates {
      "A" to true
      "B" to true
      "C" to true
    }

    // removed B, modified C, added D, added enabled E
    val olderState = pluginStates {
      "A" to true
      "C" to false
      "D" to true
      "E" to true
    }

    // changed B, removed C, also added D, added disabled E
    val newerState = pluginStates {
      "A" to true
      "B" to false
      "D" to true
      "E" to false
    }

    val actualMergedStates = mergePluginStates(baseState, olderState, newerState)

    val expectedMergedState = pluginStates {
      "A" to true
      "B" to false // modification newer than removal
      // no C because removal newer than modification
      "D" to true // both added
      "E" to false // disabled state is newer
    }

    assertPluginsState(expectedMergedState.plugins, actualMergedStates.plugins)
  }

  private fun pluginStates(build: StateBuilder.() -> Unit): SettingsSyncPluginsState {
    val builder = StateBuilder()
    builder.build()
    return SettingsSyncPluginsState(builder.states)
  }

  private class StateBuilder {
    val states = mutableMapOf<PluginId, SettingsSyncPluginsState.PluginData>()

    infix fun String.to(enabled: Boolean) {
      val pluginData = SettingsSyncPluginsState.PluginData(enabled)
      val pluginId = PluginId.getId(this)
      states[pluginId] = pluginData
    }
  }
}
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunsInEdt
class PerProjectPluginsTest {

  private val inMemoryFsRule = InMemoryFsRule()
  private val projectRule = ProjectRule()

  @Rule
  @JvmField
  val chain = RuleChain(
    inMemoryFsRule,
    projectRule,
    EdtRule(),
  )

  @Test
  fun enabledAndDisablePerProject() {
    val path = inMemoryFsRule.fs.getPath("/plugin")
    PluginBuilder()
      .randomId("enabledAndDisablePerProject")
      .build(path)

    val descriptor = loadDescriptorInTest(path)
    assertThat(descriptor).isNotNull

    val project = projectRule.project
    val pluginEnabler = PluginEnabler.getInstance() as DynamicPluginEnabler

    val loaded = pluginEnabler.updatePluginsState(
      listOf(descriptor),
      PluginEnableDisableAction.ENABLE_FOR_PROJECT,
      project,
    )
    assertTrue(loaded)
    assertRestartIsNotRequired()
    assertFalse(PluginManagerCore.isDisabled(descriptor.pluginId))
    assertTrue(PluginManagerCore.getLoadedPlugins().contains(descriptor))

    val unloaded = pluginEnabler.updatePluginsState(
      listOf(descriptor),
      PluginEnableDisableAction.DISABLE_FOR_PROJECT,
      project,
    )
    assertTrue(unloaded)
    assertRestartIsNotRequired()
    assertFalse(PluginManagerCore.isDisabled(descriptor.pluginId))
    assertFalse(PluginManagerCore.getLoadedPlugins().contains(descriptor))
  }

  private fun assertRestartIsNotRequired() {
    assertFalse(InstalledPluginsState.getInstance().isRestartRequired)
  }
}
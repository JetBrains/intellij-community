// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.rules.InMemoryFsRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Verifier
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunsInEdt
class PerProjectPluginsTest {

  private val projectRule = ProjectRule()
  private val project: ProjectEx
    get() = projectRule.project

  private val verifierRule = object : Verifier() {
    override fun verify() {
      val pluginTracker = DynamicPluginEnabler.findPluginTracker(project)
      assertNotNull(pluginTracker)

      assertTrue(pluginTracker.disabledPluginsIds.isEmpty())
      assertTrue(pluginTracker.enabledPluginsIds.isEmpty())
    }
  }

  private val inMemoryFsRule = InMemoryFsRule()

  @Rule
  @JvmField
  val chain = RuleChain(
    projectRule,
    verifierRule,
    inMemoryFsRule,
    EdtRule(),
  )

  @Test
  fun enabledAndDisablePerProject() {
    val path = inMemoryFsRule.fs.getPath("/plugin")
    PluginBuilder()
      .randomId("enabledAndDisablePerProject")
      .build(path)

    val descriptor = loadDescriptorInTest(path)
    assertNotNull(descriptor)
    val pluginId = descriptor.pluginId

    val pluginEnabler = PluginEnabler.getInstance() as? DynamicPluginEnabler
    assertNotNull(pluginEnabler)

    val loaded = pluginEnabler.updatePluginsState(
      listOf(descriptor),
      PluginEnableDisableAction.ENABLE_FOR_PROJECT,
      project,
    )
    assertTrue(loaded)
    assertRestartIsNotRequired()
    assertFalse(PluginManagerCore.isDisabled(pluginId))
    assertTrue(PluginManagerCore.getLoadedPlugins().contains(descriptor))

    val unloaded = pluginEnabler.updatePluginsState(
      listOf(descriptor),
      PluginEnableDisableAction.DISABLE_FOR_PROJECT,
      project,
    )
    assertTrue(unloaded)
    assertRestartIsNotRequired()
    assertFalse(PluginManagerCore.isDisabled(pluginId))
    assertFalse(PluginManagerCore.getLoadedPlugins().contains(descriptor))

    pluginEnabler.getPluginTracker(project)
      .stopTracking(listOf(pluginId))
  }

  private fun assertRestartIsNotRequired() {
    assertFalse(InstalledPluginsState.getInstance().isRestartRequired)
  }
}
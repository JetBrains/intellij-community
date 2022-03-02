// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Verifier
import java.nio.file.Files

@RunsInEdt
class PerProjectPluginsTest {

  private val projectRule = ProjectRule()
  private val project: ProjectEx
    get() = projectRule.project

  private val verifierRule = object : Verifier() {
    override fun verify() {
      val pluginTracker = DynamicPluginEnabler.findPluginTracker(project)
      assertThat(pluginTracker).isNotNull

      assertThat(pluginTracker!!.disabledPluginsIds).isEmpty()
      assertThat(pluginTracker.enabledPluginsIds).isEmpty()
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
    val descriptor = loadDescriptorInTest(
      PluginBuilder().randomId("enabledAndDisablePerProject"),
      Files.createTempDirectory(inMemoryFsRule.fs.getPath("/"), null),
    )
    assertThat(descriptor).isNotNull
    val pluginId = descriptor.pluginId

    val pluginEnabler = PluginEnabler.getInstance() as? DynamicPluginEnabler
    assertThat(pluginEnabler).isNotNull

    try {
      val loaded = pluginEnabler!!.updatePluginsState(
        listOf(descriptor),
        PluginEnableDisableAction.ENABLE_FOR_PROJECT,
        project,
      )
      assertThat(loaded).isTrue
      assertRestartIsNotRequired()
      assertThat(PluginManagerCore.isDisabled(pluginId)).isFalse
      assertThat(PluginManagerCore.getLoadedPlugins()).contains(descriptor)

      val unloaded = pluginEnabler.updatePluginsState(
        listOf(descriptor),
        PluginEnableDisableAction.DISABLE_FOR_PROJECT,
        project,
      )
      assertThat(unloaded).isTrue
      assertRestartIsNotRequired()
      assertThat(PluginManagerCore.isDisabled(pluginId)).isFalse
      assertThat(PluginManagerCore.getLoadedPlugins().contains(descriptor)).isFalse

      pluginEnabler.getPluginTracker(project)
        .stopTracking(listOf(pluginId))
    }
    finally {
      unloadAndUninstallPlugin(descriptor)
      assertThat(PluginManagerCore.findPlugin(pluginId)).isNull()
    }
  }

  private fun assertRestartIsNotRequired() {
    assertThat(InstalledPluginsState.getInstance().isRestartRequired).isFalse
  }
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.platform.plugins.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.loadPluginWithText
import com.intellij.platform.testFramework.plugins.buildDir
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.TempDirectory
import org.junit.Rule
import org.junit.Test

// TODO(IJPL-182235): add more tests for business logic of the service
@RunsInEdt
class DynamicPaidPluginsServiceTest {
  @Rule
  @JvmField
  val projectRule = ProjectRule()

  @Rule
  @JvmField
  val runInEdt = EdtRule()

  @Rule
  @JvmField
  val tempDir: TempDirectory = TempDirectory()

  private val rootPath get() = tempDir.rootPath
  private val pluginsDir get() = rootPath.resolve("plugins")

  @Test
  fun `test splitNotLoadedPlugins marks the plugin as loadable if all dependencies are installed`() {
    doTestSplitNotLoadedPlugins { data ->
      assertThat(data.loadableAfterRestart).containsOnly(data.testPlugin)
      assertThat(data.missingDependencies).isEmpty()
    }
  }

  @Test
  fun `test splitNotLoadedPlugins marks the plugin as loadable even if an optional dependency is not installed`() {
    doTestSplitNotLoadedPlugins(installOptionalPluginDependency = false) { data ->
      assertThat(data.loadableAfterRestart).containsOnly(data.testPlugin)
      assertThat(data.missingDependencies).isEmpty()
    }
  }

  @Test
  fun `test splitNotLoadedPlugins marks the plugin as not loadable if non-optional dependencies are not installed`() {
    doTestSplitNotLoadedPlugins(installRequiredPluginDependency = false) { data ->
      assertThat(data.loadableAfterRestart).isEmpty()
      assertThat(data.missingDependencies).containsOnly(data.testPlugin)
    }
  }

  @Test
  fun `test splitNotLoadedPlugins marks the plugin as not loadable if non-optional moduleDependency are not installed`() {
    doTestSplitNotLoadedPlugins(installMainModulePluginDependency = false) { data ->
      assertThat(data.loadableAfterRestart).isEmpty()
      assertThat(data.missingDependencies).containsOnly(data.testPlugin)
    }
  }

  private data class SplitNotLoadedPluginsData(
    val testPlugin: IdeaPluginDescriptorImpl,
    val loadableAfterRestart: List<IdeaPluginDescriptorImpl>,
    val missingDependencies: List<IdeaPluginDescriptorImpl>,
  )

  private fun doTestSplitNotLoadedPlugins(
    installOptionalPluginDependency: Boolean = true,
    installRequiredPluginDependency: Boolean = true,
    installMainModulePluginDependency: Boolean = true,
    checkPlugins: (SplitNotLoadedPluginsData) -> Unit,
  ) {
    val optDepId = "optDep"
    val requiredDepId = "requiredDep"
    val mainModulePluginDepId = "mainModulePluginDep"
    val testPluginId = "testPlugin"

    val pluginDisposables = mutableListOf<Disposable>()
    if (installOptionalPluginDependency) {
      val disposable = loadPluginWithText(plugin(optDepId) {}, pluginsDir.resolve(optDepId))
      pluginDisposables.add(disposable)
    }
    if (installRequiredPluginDependency) {
      val disposable = loadPluginWithText(plugin(requiredDepId) {}, pluginsDir.resolve(requiredDepId))
      pluginDisposables.add(disposable)
    }
    if (installMainModulePluginDependency) {
      val disposable = loadPluginWithText(plugin(mainModulePluginDepId) {}, pluginsDir.resolve(mainModulePluginDepId))
      pluginDisposables.add(disposable)
    }

    try {
      plugin(testPluginId) {
        depends(optDepId, optional = true)
        depends(requiredDepId, optional = false)
        dependencies { plugin(mainModulePluginDepId) }
      }.buildDir(pluginsDir.resolve(testPluginId))

      val testPlugin = PluginSetTestBuilder.fromPath(pluginsDir).withDisabledPlugins(testPluginId).build()
        .findInstalledPlugin(PluginId.getId(testPluginId))
      assertThat(testPlugin).isNotNull()
      assertThat(testPlugin).isInstanceOf(IdeaPluginDescriptorImpl::class.java)

      val service = DynamicPaidPluginsService.getInstance()
      val pluginIdMap = PluginManagerCore.buildPluginIdMap()
      val contentModuleIdMap = PluginManagerCore.getPluginSet().buildContentModuleIdMap()
      val (loadableAfterRestart, missingDependencies) =
        service.splitNotLoadedPlugins(listOf(testPlugin as IdeaPluginDescriptorImpl), pluginIdMap, contentModuleIdMap)
      checkPlugins(SplitNotLoadedPluginsData(testPlugin, loadableAfterRestart, missingDependencies))
    }
    finally {
      pluginDisposables.forEach { Disposer.dispose(it) }
    }
  }
}

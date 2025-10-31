// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.plugins.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.*
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.containers.map2Array
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
  private val ultimatePluginId = PluginManagerCore.ULTIMATE_PLUGIN_ID.idString

  @Test
  fun `getPluginsToEnable returns only plugins that require ultimate`() {
    val simplePlugin = plugin {}
    val paidPlugin = plugin { depends(ultimatePluginId, optional = false) }
    val paidPlugin2 = plugin { dependencies { plugin(ultimatePluginId) } }
    val optionallyPaidPlugin = plugin { depends(ultimatePluginId, optional = true) }

    val pluginSet = generatePluginSet(simplePlugin, paidPlugin, paidPlugin2, optionallyPaidPlugin)

    val explicitlyDisabledPlugins = emptySet<PluginId>()
    val pluginsToEnable = DynamicPaidPluginsService.getInstance().getPluginsToEnable(pluginSet, explicitlyDisabledPlugins)
    pluginsToEnable.assertContainsIdsExactlyInAnyOrder(paidPlugin, paidPlugin2)
  }

  @Test
  fun `getPluginsToEnable returns dependent paid plugin if all dependencies are installed and enabled`() {
    val paidPlugin = plugin { depends(ultimatePluginId, optional = false) }
    val dependentPaidPlugin = plugin {
      dependencies {
        plugin(ultimatePluginId)
        plugin(paidPlugin.id!!)
      }
    }

    val pluginSet = generatePluginSet(paidPlugin, dependentPaidPlugin)

    val explicitlyDisabledPlugins = emptySet<PluginId>()
    val pluginsToEnable = DynamicPaidPluginsService.getInstance().getPluginsToEnable(pluginSet, explicitlyDisabledPlugins)
    pluginsToEnable.assertContainsIdsExactlyInAnyOrder(paidPlugin, dependentPaidPlugin)
  }

  @Test
  fun `getPluginsToEnable does not return paid plugins that are explicitly disabled`() {
    val disabledPaidPlugin = plugin { depends(ultimatePluginId, optional = false) }

    val pluginSet = generatePluginSet(disabledPaidPlugin)

    val explicitlyDisabledPlugins = setOf(PluginId.getId(disabledPaidPlugin.id!!))
    val pluginsToEnable = DynamicPaidPluginsService.getInstance().getPluginsToEnable(pluginSet, explicitlyDisabledPlugins)
    assertThat(pluginsToEnable).isEmpty()
  }

  @Test
  fun `getPluginsToEnable does not return paid plugins with explicitly disabled dependencies`() {
    val disabledDependencyPlugin = plugin {}
    val paidPlugin = plugin {
      dependencies {
        plugin(ultimatePluginId)
        plugin(disabledDependencyPlugin.id!!)
      }
    }

    val pluginSet = generatePluginSet(disabledDependencyPlugin, paidPlugin)

    val explicitlyDisabledPlugins = setOf(PluginId.getId(disabledDependencyPlugin.id!!))
    val pluginsToEnable = DynamicPaidPluginsService.getInstance().getPluginsToEnable(pluginSet, explicitlyDisabledPlugins)
    assertThat(pluginsToEnable).isEmpty()
  }

  @Test
  fun `getPluginsToEnable does not return paid plugins with required dependencies that are not installed`() {
    val paidPluginWithMissingMainModulePluginDependency = plugin {
      dependencies {
        plugin(ultimatePluginId)
        plugin("some.missing.dependency")
      }
    }
    val paidPluginWithMissingRequiredPluginDependency = plugin {
      depends(ultimatePluginId, optional = false)
      depends("another.missing.dependency", optional = false)
    }
    val paidPluginWithMissingOptionalPluginDependency = plugin {
      depends(ultimatePluginId, optional = false)
      depends("some.missing.dependency", optional = true)
    }

    val pluginSet = generatePluginSet(paidPluginWithMissingMainModulePluginDependency, paidPluginWithMissingRequiredPluginDependency,
                                      paidPluginWithMissingOptionalPluginDependency)

    val explicitlyDisabledPlugins = emptySet<PluginId>()
    val pluginsToEnable = DynamicPaidPluginsService.getInstance().getPluginsToEnable(pluginSet, explicitlyDisabledPlugins)
    pluginsToEnable.assertContainsIdsExactlyInAnyOrder(paidPluginWithMissingOptionalPluginDependency)
  }

  @Test
  fun `getPluginsToEnable returns only compatible paid plugins`() {
    val compatiblePaidPlugin = plugin { dependencies { plugin(ultimatePluginId) } }
    val incompatibleWithHostPlatformPaidPlugin = plugin {
      depends(ultimatePluginId)
      depends("com.intellij.modules.os.incompatible")
    }
    val incompatibleBuildNumberPaidPlugin = plugin {
      depends(ultimatePluginId)
      sinceBuild = "999.0"
      untilBuild = "999.999"
    }

    val pluginSet = generatePluginSet(compatiblePaidPlugin, incompatibleWithHostPlatformPaidPlugin, incompatibleBuildNumberPaidPlugin)

    val explicitlyDisabledPlugins = emptySet<PluginId>()
    val pluginsToEnable = DynamicPaidPluginsService.getInstance().getPluginsToEnable(pluginSet, explicitlyDisabledPlugins)
    pluginsToEnable.assertContainsIdsExactlyInAnyOrder(compatiblePaidPlugin)
  }

  @Test
  fun `getPluginsToEnable does not return paid plugins that are already enabled`() {
    val paidPlugin = plugin { dependencies { plugin(ultimatePluginId) } }

    val pluginSet = generatePluginSet(paidPlugin, doNotDisable = setOf(paidPlugin))

    val explicitlyDisabledPlugins = emptySet<PluginId>()
    val pluginsToEnable = DynamicPaidPluginsService.getInstance().getPluginsToEnable(pluginSet, explicitlyDisabledPlugins)
    assertThat(pluginsToEnable).isEmpty()
  }

  private fun generatePluginSet(vararg specs: PluginSpec, doNotDisable: Set<PluginSpec> = emptySet()): PluginSet {
    plugin(ultimatePluginId) {}.buildDir(pluginsDir.resolve(ultimatePluginId))
    specs.forEach { spec ->
      spec.buildDir(pluginsDir.resolve(spec.id!!))
    }

    val disabledPluginIds = specs.filterNot { it in doNotDisable }.map2Array { it.id!! }
    return PluginSetTestBuilder.fromPath(pluginsDir)
      .withDisabledPlugins(*disabledPluginIds)
      .build()
  }

  private fun List<PluginMainDescriptor>.assertContainsIdsExactlyInAnyOrder(vararg expectedPlugins: PluginSpec) {
    val actualPluginIds = this.map { it.pluginId.idString }
    val expectedPluginIds = expectedPlugins.map2Array { it.id!! }
    assertThat(actualPluginIds).containsExactlyInAnyOrder(*expectedPluginIds)
  }
}

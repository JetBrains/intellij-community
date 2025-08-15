// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.assertj.core.api.InstanceOfAssertFactories
import org.assertj.core.api.ObjectAssert

fun ObjectAssert<PluginSet>.hasExactlyEnabledPlugins(vararg ids: String) = apply {
  extracting { it.enabledPlugins.map { plugin -> plugin.pluginId.idString } }
    .asList()
    .containsExactlyInAnyOrder(*ids)
}

fun ObjectAssert<PluginSet>.hasEnabledPlugins(vararg ids: String) = apply {
  extracting { it.enabledPlugins.map { plugin -> plugin.pluginId.idString } }
    .asInstanceOf(InstanceOfAssertFactories.LIST)
    .containsAll(ids.asList())
}

fun ObjectAssert<PluginSet>.doesNotHaveEnabledPlugins(firstId: String, vararg otherIds: String) = apply {
  extracting { it.enabledPlugins.map { plugin -> plugin.pluginId.idString } }
    .asInstanceOf(InstanceOfAssertFactories.LIST)
    .doesNotContain(firstId, *otherIds)
}

fun ObjectAssert<PluginSet>.doesNotHaveEnabledPlugins() = hasExactlyEnabledPlugins()

fun ObjectAssert<PluginSet>.hasExactlyEnabledModulesWithoutMainDescriptors(vararg ids: String) = apply {
  extracting { it.getEnabledModules().mapNotNull { plugin -> plugin.contentModuleId } }
    .asList()
    .containsExactlyInAnyOrder(*ids)
}

fun ObjectAssert<PluginSet>.doesNotHaveEnabledModulesWithoutMainDescriptors() = hasExactlyEnabledModulesWithoutMainDescriptors()

fun PluginSet.getEnabledPlugin(id: String): IdeaPluginDescriptorImpl =
  enabledPlugins.firstOrNull { it.pluginId.idString == id } ?: throw AssertionError("Plugin '$id' not found")

fun PluginSet.getEnabledPlugins(vararg ids: String): List<IdeaPluginDescriptorImpl> = ids.map { getEnabledPlugin(it) }

fun PluginSet.getPlugin(id: String): PluginMainDescriptor = allPlugins.single { id == it.pluginId.idString }

fun PluginSet.getEnabledModule(id: String): IdeaPluginDescriptorImpl =
  findEnabledModule(PluginModuleId(id)) ?: throw AssertionError("Module '$id' not found")

fun PluginSet.getEnabledModules(vararg ids: String): List<IdeaPluginDescriptorImpl> = ids.map { getEnabledModule(it) }
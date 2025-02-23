// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLoader
import org.assertj.core.api.InstanceOfAssertFactories
import org.assertj.core.api.ObjectAssert

fun ObjectAssert<PluginSet>.hasExactlyEnabledPlugins(vararg ids: String) = apply {
  extracting { it.enabledPlugins.map { plugin -> plugin.pluginId.idString } }
    .asList()
    .containsExactlyInAnyOrder(*ids)
}

fun ObjectAssert<PluginSet>.doesNotHaveEnabledPlugins() = hasExactlyEnabledPlugins()

fun ObjectAssert<PluginSet>.hasExactlyEnabledModulesWithoutMainDescriptors(vararg ids: String) = apply {
  extracting { it.getEnabledModules().mapNotNull { plugin -> plugin.moduleName } }
    .asList()
    .containsExactlyInAnyOrder(*ids)
}

fun ObjectAssert<PluginSet>.doesNotHaveEnabledModulesWithoutMainDescriptors() = hasExactlyEnabledModulesWithoutMainDescriptors()

fun ObjectAssert<IdeaPluginDescriptorImpl>.hasClassloaderParents(vararg parentDescriptors: IdeaPluginDescriptorImpl) = apply {
  extracting { (it.classLoader as PluginClassLoader)._getParents() }
    .asInstanceOf(InstanceOfAssertFactories.LIST)
    .contains(*parentDescriptors)
}

fun ObjectAssert<IdeaPluginDescriptorImpl>.doesNotHaveClassloaderParents(vararg parentDescriptors: IdeaPluginDescriptorImpl) = apply {
  extracting { (it.classLoader as PluginClassLoader)._getParents() }
    .asInstanceOf(InstanceOfAssertFactories.LIST)
    .doesNotContain(*parentDescriptors)
}

fun PluginSet.getEnabledPlugin(id: String): IdeaPluginDescriptorImpl =
  enabledPlugins.firstOrNull { it.pluginId.idString == id } ?: throw AssertionError("Plugin '$id' not found")

fun PluginSet.getEnabledPlugins(vararg ids: String): List<IdeaPluginDescriptorImpl> = ids.map { getEnabledPlugin(it) }

fun PluginSet.getEnabledModule(id: String): IdeaPluginDescriptorImpl =
  findEnabledModule(id) ?: throw AssertionError("Module '$id' not found")

fun PluginSet.getEnabledModules(vararg ids: String): List<IdeaPluginDescriptorImpl> = ids.map { getEnabledModule(it) }
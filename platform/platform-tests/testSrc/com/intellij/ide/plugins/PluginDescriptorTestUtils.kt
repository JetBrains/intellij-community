// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLoader
import org.assertj.core.api.InstanceOfAssertFactories
import org.assertj.core.api.ObjectAssert


fun ObjectAssert<IdeaPluginDescriptorImpl>.hasDirectParentClassloaders(vararg parentDescriptors: IdeaPluginDescriptorImpl) = apply {
  extracting { (it.classLoader as PluginClassLoader)._getParents() }
    .asInstanceOf(InstanceOfAssertFactories.LIST)
    .contains(*parentDescriptors)
}

fun ObjectAssert<IdeaPluginDescriptorImpl>.doesNotHaveDirectParentClassloaders(vararg parentDescriptors: IdeaPluginDescriptorImpl) = apply {
  extracting { (it.classLoader as PluginClassLoader)._getParents() }
    .asInstanceOf(InstanceOfAssertFactories.LIST)
    .doesNotContain(*parentDescriptors)
}

fun ObjectAssert<IdeaPluginDescriptorImpl>.hasTransitiveParentClassloaders(vararg parentDescriptors: IdeaPluginDescriptorImpl) = apply {
  extracting { (it.classLoader as PluginClassLoader).getAllParentsClassLoaders() }
    .asInstanceOf(InstanceOfAssertFactories.ARRAY)
    .contains(*parentDescriptors.map { it.classLoader }.toTypedArray())
}

fun ObjectAssert<IdeaPluginDescriptorImpl>.doesNotHaveTransitiveParentClassloaders(vararg parentDescriptors: IdeaPluginDescriptorImpl) = apply {
  extracting { (it.classLoader as PluginClassLoader).getAllParentsClassLoaders() }
    .asInstanceOf(InstanceOfAssertFactories.ARRAY)
    .doesNotContain(*parentDescriptors.map { it.classLoader }.toTypedArray())
}

fun ObjectAssert<IdeaPluginDescriptorImpl>.isMarkedEnabled() = apply {
  extracting { it.isEnabled }
    .asInstanceOf(InstanceOfAssertFactories.BOOLEAN)
    .isTrue
}

fun ObjectAssert<IdeaPluginDescriptorImpl>.isNotMarkedEnabled() = apply {
  extracting { it.isEnabled }
    .asInstanceOf(InstanceOfAssertFactories.BOOLEAN)
    .isFalse
}

fun ObjectAssert<IdeaPluginDescriptorImpl>.hasExactlyEnabledContentModules(vararg ids: String) = apply {
  extracting { it.content.modules.mapNotNull { it.getDescriptorOrNull()?.takeIf { it.isEnabled }?.moduleName } }
    .asList()
    .containsExactly(*ids)
}

fun ObjectAssert<IdeaPluginDescriptorImpl>.doesNotHaveEnabledContentModules() = hasExactlyEnabledContentModules()

fun ObjectAssert<IdeaPluginDescriptorImpl>.hasExactlyApplicationServices(vararg impls: String) = apply {
  extracting { it.appContainerDescriptor.services.mapNotNull { it.serviceImplementation } }
    .asList()
    .containsExactly(*impls)
}

fun ObjectAssert<IdeaPluginDescriptorImpl>.hasExactlyExtensionPointsNames(vararg names: String) = apply {
  extracting { it.appContainerDescriptor.extensionPoints.map { it.name } }
    .asList()
    .containsExactly(*names)
}
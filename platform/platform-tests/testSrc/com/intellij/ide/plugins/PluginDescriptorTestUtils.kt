// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLoader
import org.assertj.core.api.InstanceOfAssertFactories
import org.assertj.core.api.ObjectAssert


fun ObjectAssert<out IdeaPluginDescriptorImpl>.hasDirectParentClassloaders(vararg parentDescriptors: IdeaPluginDescriptorImpl) = apply {
  extracting { (it.classLoader as PluginClassLoader)._getParents() }
    .asInstanceOf(InstanceOfAssertFactories.LIST)
    .containsExactlyInAnyOrder(*parentDescriptors)
}

fun ObjectAssert<out IdeaPluginDescriptorImpl>.doesNotHaveDirectParentClassloaders(vararg parentDescriptors: IdeaPluginDescriptorImpl) = apply {
  extracting { (it.classLoader as PluginClassLoader)._getParents() }
    .asInstanceOf(InstanceOfAssertFactories.LIST)
    .doesNotContain(*parentDescriptors)
}

fun ObjectAssert<out IdeaPluginDescriptorImpl>.hasTransitiveParentClassloaders(vararg parentDescriptors: IdeaPluginDescriptorImpl) = apply {
  extracting { (it.classLoader as PluginClassLoader).getAllParentsClassLoaders() }
    .asInstanceOf(InstanceOfAssertFactories.ARRAY)
    .contains(*parentDescriptors.map { it.classLoader }.toTypedArray())
}

fun ObjectAssert<out IdeaPluginDescriptorImpl>.doesNotHaveTransitiveParentClassloaders(vararg parentDescriptors: IdeaPluginDescriptorImpl) = apply {
  extracting { (it.classLoader as PluginClassLoader).getAllParentsClassLoaders() }
    .asInstanceOf(InstanceOfAssertFactories.ARRAY)
    .doesNotContain(*parentDescriptors.map { it.classLoader }.toTypedArray())
}

fun ObjectAssert<out IdeaPluginDescriptorImpl>.isMarkedEnabled() = apply {
  extracting { it.isEnabled }
    .asInstanceOf(InstanceOfAssertFactories.BOOLEAN)
    .isTrue
}

fun ObjectAssert<out IdeaPluginDescriptorImpl>.isNotMarkedEnabled() = apply {
  extracting { it.isEnabled }
    .asInstanceOf(InstanceOfAssertFactories.BOOLEAN)
    .isFalse
}

fun ObjectAssert<out IdeaPluginDescriptorImpl>.hasExactlyEnabledContentModules(vararg ids: String) = apply {
  extracting { it.contentModules.mapNotNull { it.takeIf { it.isEnabled }?.moduleId?.id } }
    .asList()
    .containsExactly(*ids)
}

fun ObjectAssert<out IdeaPluginDescriptorImpl>.doesNotHaveEnabledContentModules() = hasExactlyEnabledContentModules()

fun ObjectAssert<out IdeaPluginDescriptorImpl>.hasExactlyApplicationServices(vararg impls: String) = apply {
  extracting { it.appContainerDescriptor.services.mapNotNull { it.serviceImplementation } }
    .asList()
    .containsExactly(*impls)
}

fun ObjectAssert<out IdeaPluginDescriptorImpl>.hasExactlyExtensionPointsNames(vararg names: String) = apply {
  extracting { it.appContainerDescriptor.extensionPoints.map { it.name } }
    .asList()
    .containsExactly(*names)
}

internal fun IdeaPluginDescriptorImpl.loadClassInsideSelf(fqn: String): Class<*> {
  return (classLoader as PluginClassLoader).loadClassInsideSelf(fqn) ?: error("Class '$fqn' not found in $this")
}

internal inline fun <reified T> IdeaPluginDescriptorImpl.loadClassInsideSelf(): Class<*> {
  val fqn = T::class.qualifiedName!!
  return ((classLoader as PluginClassLoader).loadClassInsideSelf(fqn) ?: error("Class '$fqn' not found in $this"))
}
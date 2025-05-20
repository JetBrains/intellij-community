// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.serviceContainer

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PrecomputedExtensionModel(
  @JvmField val extensionPoints: List<Pair<IdeaPluginDescriptor, List<ExtensionPointDescriptor>>>,
  @JvmField val nameToExtensions: Map<String, List<Pair<IdeaPluginDescriptor, List<ExtensionDescriptor>>>>,
)

private val EMPTY = PrecomputedExtensionModel(extensionPoints = java.util.List.of(), nameToExtensions = java.util.Map.of())

@ApiStatus.Internal
fun precomputeModuleLevelExtensionModel(): PrecomputedExtensionModel {
  val modules = PluginManagerCore.getPluginSet().enabledPlugins

  var extensionPointTotalCount = 0
  val mutableNameToExtensions = HashMap<String, MutableList<Pair<IdeaPluginDescriptor, List<ExtensionDescriptor>>>>()

  // step 1 - collect container level extension points
  val extensionPointDescriptors = ArrayList<Pair<IdeaPluginDescriptor, List<ExtensionPointDescriptor>>>()
  executeRegisterTask(modules) { pluginDescriptor ->
    val list = pluginDescriptor.moduleContainerDescriptor.extensionPoints
    if (list.isNotEmpty()) {
      extensionPointDescriptors.add(pluginDescriptor to list)
      extensionPointTotalCount += list.size
      for (descriptor in list) {
        mutableNameToExtensions.put(descriptor.getQualifiedName(pluginDescriptor), ArrayList())
      }
    }
  }

  if (extensionPointDescriptors.isEmpty() || mutableNameToExtensions.isEmpty()) {
    return EMPTY
  }

  val nameToExtensions = java.util.Map.copyOf(mutableNameToExtensions)
  // step 2 - collect container level extensions
  executeRegisterTask(modules) { pluginDescriptor ->
    val map = pluginDescriptor.extensions
    for ((name, list) in map.entries) {
      nameToExtensions.get(name)?.add(pluginDescriptor to list)
    }
  }

  extensionPointDescriptors.trimToSize()
  return PrecomputedExtensionModel(extensionPoints = extensionPointDescriptors, nameToExtensions = nameToExtensions)
}

private fun executeRegisterTask(modules: List<IdeaPluginDescriptorImpl>, task: (IdeaPluginDescriptorImpl) -> Unit) {
  for (module in modules) {
    task(module)
    executeRegisterTaskForOldContent(mainPluginDescriptor = module, task = task)
  }
}

@ApiStatus.Internal
fun executeRegisterTaskForOldContent(mainPluginDescriptor: IdeaPluginDescriptorImpl, task: (IdeaPluginDescriptorImpl) -> Unit) {
  for (dep in mainPluginDescriptor.dependencies) {
    val subDescriptor = dep.subDescriptor
    if (subDescriptor?.pluginClassLoader == null) {
      continue
    }

    task(subDescriptor)

    for (subDep in subDescriptor.dependencies) {
      val d = subDep.subDescriptor
      if (d?.pluginClassLoader != null) {
        task(d)
        assert(d.dependencies.isEmpty() || d.dependencies.all { it.subDescriptor == null })
      }
    }
  }
}
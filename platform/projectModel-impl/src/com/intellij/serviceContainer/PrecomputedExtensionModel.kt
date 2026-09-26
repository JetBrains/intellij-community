// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.serviceContainer

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PrecomputedExtensionModel(
  @JvmField val extensionPoints: List<Pair<IdeaPluginDescriptor, List<ExtensionPointDescriptor>>>,
  @JvmField val nameToExtensions: Map<String, List<Pair<IdeaPluginDescriptor, List<ExtensionDescriptor>>>>,
  @JvmField val services: List<Pair<IdeaPluginDescriptor, List<ServiceDescriptor>>>,
)

private val EMPTY = PrecomputedExtensionModel(
  extensionPoints = java.util.List.of(),
  nameToExtensions = java.util.Map.of(),
  services = java.util.List.of(),
)

// checkModuleLevelServiceAndExtensionRegistration validates that no services or extensions for `executeRegisterTaskForOldContent`
@ApiStatus.Internal
fun precomputeModuleLevelExtensionModel(): PrecomputedExtensionModel {
  val nameToExtensions = HashMap<String, MutableList<Pair<IdeaPluginDescriptor, List<ExtensionDescriptor>>>>()

  // step 1 - collect module level extension points
  val extensionPointDescriptors = ArrayList<Pair<IdeaPluginDescriptor, List<ExtensionPointDescriptor>>>()
  val allServices = ArrayList<Pair<IdeaPluginDescriptor, List<ServiceDescriptor>>>()

  for (module in PluginManagerCore.getPluginSet().sequenceResolvedSortedDescriptorsForRegistration()) {
    val list = module.moduleContainerDescriptor.extensionPoints
    if (list.isNotEmpty()) {
      extensionPointDescriptors.add(module to list)
      for (descriptor in list) {
        nameToExtensions.put(descriptor.getQualifiedName(module), ArrayList())
      }
    }

    val services = module.moduleContainerDescriptor.services
    if (services.isNotEmpty()) {
      allServices.add(module to services)
    }
  }

  if ((extensionPointDescriptors.isEmpty() || nameToExtensions.isEmpty()) && allServices.isEmpty()) {
    return EMPTY
  }

  // step 2 - collect module level extensions
  for (module in PluginManagerCore.getPluginSet().sequenceResolvedSortedDescriptorsForRegistration()) {
    val map = module.extensions
    for ((name, list) in map.entries) {
      nameToExtensions.get(name)?.add(module to list)
    }
  }

  return PrecomputedExtensionModel(extensionPoints = extensionPointDescriptors, nameToExtensions = nameToExtensions, services = allServices)
}
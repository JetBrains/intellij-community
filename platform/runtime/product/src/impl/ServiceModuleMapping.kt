// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.impl

import com.intellij.platform.runtime.product.PluginModuleGroup
import com.intellij.platform.runtime.product.ProductModules
import com.intellij.platform.runtime.product.RuntimeModuleGroup
import com.intellij.platform.runtime.repository.MalformedRepositoryException
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor
import com.intellij.util.SmartList
import com.intellij.util.containers.FList

/**
 * Provides mapping from modules which aren't explicitly added to the main or plugin module groups, but used in dependencies of some plugin
 * module group, to the corresponding plugin. 
 */
interface ServiceModuleMapping {
  fun getAdditionalModules(pluginModuleGroup: RuntimeModuleGroup): List<RuntimeModuleDescriptor>
  
  companion object {
    fun buildMapping(productModules: ProductModules, includeDebugInfoInErrorMessage: Boolean = false): ServiceModuleMapping {
      val mainGroupModulesSet = productModules.mainModuleGroup.includedModules.mapTo(HashSet()) { it.moduleDescriptor }
      val moduleOutsideGroupsToPlugin = HashMap<RuntimeModuleDescriptor, PluginModuleGroup>()
      val dependencyPathToModule = if (includeDebugInfoInErrorMessage) HashMap<RuntimeModuleDescriptor, FList<RuntimeModuleDescriptor>>() else null
      val pluginGroupModules = HashMap<RuntimeModuleDescriptor, PluginModuleGroup>()
      val serviceModules = HashMap<RuntimeModuleGroup, MutableList<RuntimeModuleDescriptor>>()
      for (moduleGroup in productModules.bundledPluginModuleGroups) {
        moduleGroup.includedModules.associateByTo(pluginGroupModules, { it.moduleDescriptor }, { moduleGroup })
      }

      val errors = SmartList<String>()
      fun collectDependencies(descriptor: RuntimeModuleDescriptor, pluginGroup: PluginModuleGroup, dependencyPath: FList<RuntimeModuleDescriptor>?) {
        val toSkip = dependenciesToSkip[descriptor.moduleId.stringId] ?: emptySet()
        for (dependency in descriptor.dependencies) {
          if (dependency !in mainGroupModulesSet && dependency !in pluginGroupModules && dependency.moduleId.stringId !in toSkip) {
            val previousGroup = moduleOutsideGroupsToPlugin[dependency]
            if (previousGroup == null) {
              moduleOutsideGroupsToPlugin[dependency] = pluginGroup
              if (dependencyPathToModule != null && dependencyPath != null) {
                dependencyPathToModule[dependency] = dependencyPath
              }
              serviceModules.getOrPut(pluginGroup) { ArrayList() }.add(dependency)
              collectDependencies(dependency, pluginGroup, dependencyPath?.prepend(dependency))
            }
            else if (previousGroup != pluginGroup) {
              val currentPath = showPath(dependency, dependencyPath)
              val previousPath = showPath(dependency, dependencyPathToModule?.get(dependency))
              errors.add("Modules from two plugins depend on module '${dependency.moduleId.stringId}': " +
                         "'${previousGroup.mainModule.moduleId.stringId}'$previousPath and '${pluginGroup.mainModule.moduleId.stringId}'$currentPath")
            }
          }
        }
      }

      for (moduleGroup in productModules.bundledPluginModuleGroups) {
        for (includedModule in moduleGroup.includedModules) {
          val initialDependencyPath = if (includeDebugInfoInErrorMessage) FList.singleton(includedModule.moduleDescriptor) else null
          collectDependencies(includedModule.moduleDescriptor, moduleGroup, initialDependencyPath)
        }
      }

      if (errors.isNotEmpty()) {
        throw MalformedRepositoryException("Failed to build mapping for service modules in plugins:\n${errors.joinToString("\n")}")
      }
      return ServiceModuleMappingImpl(serviceModules)
    }
    
    private fun showPath(dependency: RuntimeModuleDescriptor, path: FList<RuntimeModuleDescriptor>?): String =
      if (path != null) " (${path.prepend(dependency).joinToString(" <- ") { it.moduleId.stringId }})" else ""

    //has a duplicate in runtimeModuleRepositoryGenerator.kt
    private val dependenciesToSkip = mapOf(
      //RDCT-488
      "intellij.performanceTesting" to setOf(
        "intellij.platform.vcs.impl",
        "intellij.platform.vcs.log",
        "intellij.platform.vcs.log.impl",
      )
    )
  }
}

private class ServiceModuleMappingImpl(private val serviceModules: Map<RuntimeModuleGroup, List<RuntimeModuleDescriptor>>) : ServiceModuleMapping {
  override fun getAdditionalModules(pluginModuleGroup: RuntimeModuleGroup): List<RuntimeModuleDescriptor> {
    return serviceModules[pluginModuleGroup] ?: emptyList()
  }
}

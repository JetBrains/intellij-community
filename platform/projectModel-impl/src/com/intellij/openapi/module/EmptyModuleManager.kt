// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module

import com.intellij.openapi.module.impl.createGrouper
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull

class EmptyModuleManager(private val project: Project) : ModuleManager() {
  override fun hasModuleGroups(): Boolean = false

  override fun newModule(filePath: String, moduleTypeId: String): Nothing = throw UnsupportedOperationException()

  override fun loadModule(filePath: String): Nothing = throw UnsupportedOperationException()

  override fun disposeModule(module: Module) {
  }

  override fun getModules(): Array<Module> = emptyArray<Module>()

  override fun findModuleByName(name: String): Nothing? = null

  override fun getSortedModules(): Array<Module> = emptyArray<Module>()

  override fun moduleDependencyComparator(): Nothing = throw UnsupportedOperationException()

  override fun getModuleDependentModules(module: Module): List<Module> = emptyList<Module>()

  override fun isModuleDependent(@NotNull module: Module, @NotNull onModule: Module): Boolean = false

  override fun moduleGraph(): Nothing = moduleGraph(true)

  override fun moduleGraph(includeTests: Boolean): Nothing = throw UnsupportedOperationException()

  override fun getModifiableModel(): Nothing = throw UnsupportedOperationException()

  override fun getModuleGroupPath(module: Module): Array<String> = emptyArray<String>()

  override fun setUnloadedModules(unloadedModuleNames: MutableList<String>) {
  }

  override fun getModuleGrouper(model: ModifiableModuleModel?): ModuleGrouper {
    return createGrouper(project, model)
  }

  override fun getAllModuleDescriptions(): List<ModuleDescription> = emptyList<ModuleDescription>()

  override fun getUnloadedModuleDescriptions(): List<UnloadedModuleDescription> = emptyList<UnloadedModuleDescription>()

  override fun getUnloadedModuleDescription(moduleName: String): Nothing? = null

  override fun removeUnloadedModules(unloadedModules: MutableCollection<out UnloadedModuleDescription>) {
  }
}
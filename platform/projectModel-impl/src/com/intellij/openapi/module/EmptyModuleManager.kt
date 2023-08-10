// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module

import com.intellij.openapi.module.impl.createGrouper
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import java.nio.file.Path

class EmptyModuleManager(private val project: Project) : ModuleManager() {
  override fun hasModuleGroups(): Boolean = false

  override fun newModule(filePath: String, moduleTypeId: String): Nothing = throw UnsupportedOperationException()

  override fun loadModule(filePath: String) = throw UnsupportedOperationException()

  override fun loadModule(file: Path) = throw UnsupportedOperationException()

  override fun disposeModule(module: Module) {
  }

  override val modules: Array<Module>
    get() = emptyArray()

  override fun findModuleByName(name: String): Nothing? = null

  override val sortedModules: Array<Module>
    get() = emptyArray()

  override fun moduleDependencyComparator(): Nothing = throw UnsupportedOperationException()

  override fun getModuleDependentModules(module: Module): List<Module> = emptyList()

  override fun isModuleDependent(@NotNull module: Module, @NotNull onModule: Module): Boolean = false

  override fun moduleGraph(): Nothing = moduleGraph(true)

  override fun moduleGraph(includeTests: Boolean): Nothing = throw UnsupportedOperationException()

  override fun getModifiableModel(): Nothing = throw UnsupportedOperationException()

  override fun getModuleGroupPath(module: Module): Array<String> = emptyArray()

  override suspend fun setUnloadedModules(unloadedModuleNames: List<String>) {
  }

  override fun setUnloadedModulesSync(unloadedModuleNames: List<String>) {
  }

  override fun getModuleGrouper(model: ModifiableModuleModel?): ModuleGrouper {
    return createGrouper(project, model)
  }

  override val allModuleDescriptions: List<ModuleDescription>
    get() = emptyList()


  override val unloadedModuleDescriptions: List<UnloadedModuleDescription>
    get() = emptyList()

  override fun getUnloadedModuleDescription(moduleName: String): Nothing? = null

  override fun removeUnloadedModules(unloadedModules: Collection<UnloadedModuleDescription>) {
  }
}
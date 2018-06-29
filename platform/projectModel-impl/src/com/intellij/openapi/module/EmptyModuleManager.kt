/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.module

import com.intellij.openapi.module.impl.createGrouper
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBus
import org.jetbrains.annotations.NotNull

class EmptyModuleManager(private val project: Project, messageBus: MessageBus) : ModuleManager() {
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

  override fun removeUnloadedModules(unloadedModules: MutableCollection<UnloadedModuleDescription>) {
  }
}
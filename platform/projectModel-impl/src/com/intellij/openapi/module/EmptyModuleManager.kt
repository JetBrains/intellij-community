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

import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBus
import org.jetbrains.annotations.NotNull

class EmptyModuleManager(project: Project, messageBus: MessageBus) : ModuleManager() {
  override fun hasModuleGroups() = false

  override fun newModule(filePath: String, moduleTypeId: String) = throw UnsupportedOperationException()

  override fun loadModule(filePath: String) = throw UnsupportedOperationException()

  override fun disposeModule(module: Module) {
  }

  override fun getModules() = emptyArray<Module>()

  override fun findModuleByName(name: String) = null

  override fun getSortedModules() = emptyArray<Module>()

  override fun moduleDependencyComparator() = throw UnsupportedOperationException()

  override fun getModuleDependentModules(module: Module) = emptyList<Module>()

  override fun isModuleDependent(@NotNull module: Module, @NotNull onModule: Module) = false

  override fun moduleGraph() = moduleGraph(true)

  override fun moduleGraph(includeTests: Boolean) = throw UnsupportedOperationException()

  override fun getModifiableModel() = throw UnsupportedOperationException()

  override fun getModuleGroupPath(module: Module) = emptyArray<String>()

  override fun setUnloadedModules(unloadedModuleNames: MutableList<String>) {
  }

  override fun getAllModuleDescriptions() = emptyList<ModuleDescription>()

  override fun getUnloadedModuleDescriptions() = emptyList<UnloadedModuleDescription>()

  override fun getUnloadedModuleDescription(moduleName: String) = null

  override fun removeUnloadedModules(unloadedModules: MutableCollection<UnloadedModuleDescription>) {
  }
}
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
package com.intellij.openapi.module.impl

import com.intellij.openapi.module.LoadedModuleDescription
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager

/**
 * @author nik
 */
class LoadedModuleDescriptionImpl(private val module: Module): LoadedModuleDescription {
  override fun getModule(): Module = module

  override fun getName(): String = module.name

  override fun getDependencyModuleNames(): List<String> = ModuleRootManager.getInstance(module).dependencyModuleNames.asList()

  override fun equals(other: Any?): Boolean = other is LoadedModuleDescriptionImpl && module == other.module

  override fun hashCode(): Int = module.hashCode()
}
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
class LoadedModuleDescriptionImpl(override val module: Module): LoadedModuleDescription {
  override val name: String
    get() = module.name

  override val dependencyModuleNames: List<String>
    get() = ModuleRootManager.getInstance(module).dependencyModuleNames.asList()
}
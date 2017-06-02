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

import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a description of a module which may be either loaded into the project or unloaded. Use this class only if you need to process
 * all modules including unloaded, in other cases [Module] should be used instead.
 * @see [ModuleManager.getUnloadedModuleDescriptions]
 * @author nik
 */
@ApiStatus.Experimental
interface ModuleDescription {
  val name: String

  /**
   * Names of the modules on which the current module depend.
   */
  val dependencyModuleNames: List<String>
}

@ApiStatus.Experimental
interface UnloadedModuleDescription : ModuleDescription {
  val contentRoots: List<VirtualFilePointer>
  val groupPath: List<String>
}

@ApiStatus.Experimental
interface LoadedModuleDescription : ModuleDescription {
  val module: Module
}
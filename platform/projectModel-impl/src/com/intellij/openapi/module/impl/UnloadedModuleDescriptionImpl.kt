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

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.module.UnloadedModuleDescription
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.serialization.JpsGlobalLoader
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import java.nio.file.Paths

/**
 * @author nik
 */
class UnloadedModuleDescriptionImpl(val modulePath: ModulePath,
                                    private val dependencyModuleNames: List<String>,
                                    private val contentRoots: List<VirtualFilePointer>) : UnloadedModuleDescription {
  override fun getGroupPath(): List<String> = modulePath.group?.split(ModuleManagerImpl.MODULE_GROUP_SEPARATOR) ?: emptyList()

  override fun getName(): String = modulePath.moduleName

  override fun getContentRoots(): List<VirtualFilePointer> = contentRoots

  override fun getDependencyModuleNames(): List<String> = dependencyModuleNames

  override fun equals(other: Any?): Boolean = other is UnloadedModuleDescriptionImpl && name == other.name

  override fun hashCode(): Int = name.hashCode()

  companion object {
    @JvmStatic
    fun createFromPaths(paths: Collection<ModulePath>, parentDisposable: Disposable): List<UnloadedModuleDescriptionImpl> {
      val pathVariables = JpsGlobalLoader.computeAllPathVariables(PathManager.getOptionsPath())
      val modules = JpsProjectLoader.loadModules(paths.map { Paths.get(it.path) }, null, pathVariables)
      val pathsByName = paths.associateBy { it.moduleName }
      return modules.map { create(pathsByName[it.name]!!, it, parentDisposable) }
    }

    private fun create(path: ModulePath, module: JpsModule, parentDisposable: Disposable): UnloadedModuleDescriptionImpl {
      val dependencyModuleNames = module.dependenciesList.dependencies
        .filterIsInstance(JpsModuleDependency::class.java)
        .map { it.moduleReference.moduleName }

      val pointerManager = VirtualFilePointerManager.getInstance()
      return UnloadedModuleDescriptionImpl(path, dependencyModuleNames, module.contentRootsList.urls.map {pointerManager.create(it, parentDisposable, null)})
    }
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.module.ModuleManager.Companion.getInstanceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.graph.Graph
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.io.IOException
import java.nio.file.Path

/**
 * Provides services for working with the modules of a project.
 */
@ApiStatus.NonExtendable
abstract class ModuleManager : SimpleModificationTracker() {
  companion object {
    /**
     * Returns the module manager instance for the current project.
     * In coroutines, use [getInstanceAsync] instead.
     *
     * @param project the project for which the module manager is requested.
     * @return the module manager instance.
     */
    @JvmStatic
    @RequiresBlockingContext
    fun getInstance(project: Project): ModuleManager = project.service()

    suspend fun getInstanceAsync(project: Project): ModuleManager = project.serviceAsync()

    fun getInstanceIfDefined(project: Project): ModuleManager? = project.serviceOrNull()
  }

  /**
   * Creates a module of the specified type at the specified path and adds it to the project
   * to which the module manager is related.
   *
   * @param filePath     path to an *.iml file where module configuration will be saved; name of the module will be equal to the file name without extension.
   * @param moduleTypeId the ID of the module type to create.
   * @return the module instance.
   */
  abstract fun newModule(filePath: @NonNls String, moduleTypeId: String): Module

  fun newModule(file: Path, moduleTypeId: String): Module {
    return newModule(file.toString().replace(File.separatorChar, '/'), moduleTypeId)
  }

  /**
   * Creates a non-persistent module of the specified type and adds it to the project
   * to which the module manager is related. [.commit] must be called to
   * bring the changes in effect.
   *
   * In contrast with modules created by [.newModule],
   * non-persistent modules aren't stored on a filesystem and aren't being written
   * in a project XML file. When IDE closes, all non-persistent modules vanishes out.
   */
  @ApiStatus.Experimental
  open fun newNonPersistentModule(moduleName: String, id: String): Module {
    throw UnsupportedOperationException()
  }

  @Deprecated("Use {@link #loadModule(Path)}")
  @Throws(IOException::class, JDOMException::class, ModuleWithNameAlreadyExists::class)
  abstract fun loadModule(filePath: String): Module

  /**
   * Loads a module from an .iml file with the specified path and adds it to the project.
   *
   * @param file the path to load the module from.
   * @return the module instance.
   * @throws IOException                 if an I/O error occurred when loading the module file.
   * @throws ModuleWithNameAlreadyExists if a module with such a name already exists in the project.
   */
  @Throws(IOException::class, ModuleWithNameAlreadyExists::class)
  abstract fun loadModule(file: Path): Module

  /**
   * Disposes of the specified module and removes it from the project.
   *
   * @param module the module to remove.
   */
  abstract fun disposeModule(module: Module)

  /**
   * Returns the list of all modules in the project.
   *
   * @return the array of modules.
   */
  abstract val modules: Array<Module>

  /**
   * Returns the project module with the specified name.
   *
   * @param name the name of the module to find.
   * @return the module instance, or null if no module with such name exists.
   */
  abstract fun findModuleByName(name: @NonNls String): Module?

  /**
   * Returns the list of modules sorted by dependency (the modules which do not depend
   * on anything are in the beginning of the list, a module which depends on another module
   * follows it in the list).
   *
   * @return the sorted array of modules.
   */
  abstract val sortedModules: Array<Module>

  /**
   * Returns the module comparator which can be used for sorting modules by dependency
   * (the modules which do not depend on anything are in the beginning of the list,
   * a module which depends on another module follows it in the list).
   *
   * @return the module comparator instance.
   */
  abstract fun moduleDependencyComparator(): Comparator<Module>

  /**
   * Returns the list of modules which directly depend on the specified module.
   *
   * @param module the module for which the list of dependent modules is requested.
   * @return list of *modules that depend on* given module.
   * @see ModuleUtilCore.getAllDependentModules
   */
  abstract fun getModuleDependentModules(module: Module): List<Module>

  /**
   * Checks if one of the specified modules directly depends on the other module.
   *
   * @param module   the module to check the dependency for.
   * @param onModule the module on which `module` may depend.
   * @return true if `module` directly depends on `onModule`, false otherwise.
   */
  abstract fun isModuleDependent(module: Module, onModule: Module): Boolean

  /**
   * Returns the graph of dependencies between modules in the project.
   *
   * @return the module dependency graph.
   */
  abstract fun moduleGraph(): Graph<Module>

  /**
   * Returns the graph of dependencies between modules in the project.
   *
   * @param includeTests whether test-only dependencies should be included
   * @return the module dependency graph.
   */
  abstract fun moduleGraph(includeTests: Boolean): Graph<Module>

  /**
   * Returns the model for the list of modules in the project, which can be used to add,
   * remove or modify modules.
   *
   * @return the modifiable model instance.
   */
  abstract fun getModifiableModel(): ModifiableModuleModel

  /**
   * Returns the path to the group to which was explicitly set for the specified module, as an array of group names starting from the project root.
   *
   * **Use [ModuleGrouper.getGroupPath] instead.** Explicit module groups are replaced by automatic module grouping accordingly to qualified 
   * names of modules, see [IDEA-166061](https://youtrack.jetbrains.com/issue/IDEA-166061) for details.
   * 
   * @return the path to the group for the module, or null if the module does not belong to any group.
   */
  @ApiStatus.Internal
  open fun getModuleGroupPath(module: Module): Array<String>? = null

  /**
   * Returns `true` if at least one of the modules has an explicitly specified module group. Note that explicit module groups are replaced
   * by automatic grouping, so this method is left for compatibility with some old projects only.
   */
  @ApiStatus.Internal
  open fun hasModuleGroups(): Boolean = false

  /**
   * @return description of all modules in the project including unloaded
   */
  @get:ApiStatus.Experimental
  abstract val allModuleDescriptions: Collection<ModuleDescription>

  @get:ApiStatus.Experimental
  abstract val unloadedModuleDescriptions: Collection<UnloadedModuleDescription>

  @ApiStatus.Experimental
  abstract fun getUnloadedModuleDescription(moduleName: String): UnloadedModuleDescription?

  abstract fun getModuleGrouper(model: ModifiableModuleModel?): ModuleGrouper

  /**
   * Specify list of modules which will be unloaded from the project.
   * @see UnloadedModuleDescription
   */
  @ApiStatus.Experimental
  abstract suspend fun setUnloadedModules(unloadedModuleNames: List<String>)

  @Deprecated("Use setUnloadedModules")
  @TestOnly
  abstract fun setUnloadedModulesSync(unloadedModuleNames: List<String>)

  @ApiStatus.Experimental
  open fun removeUnloadedModules(unloadedModules: Collection<UnloadedModuleDescription>) {
  }
}
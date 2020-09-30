// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.util.graph.Graph;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Provides services for working with the modules of a project.
 */
@ApiStatus.NonExtendable
public abstract class ModuleManager extends SimpleModificationTracker {
  /**
   * Returns the module manager instance for the current project.
   *
   * @param project the project for which the module manager is requested.
   * @return the module manager instance.
   */
  public static ModuleManager getInstance(@NotNull Project project) {
    return project.getComponent(ModuleManager.class);
  }

  /**
   * Creates a module of the specified type at the specified path and adds it to the project
   * to which the module manager is related.
   *
   * @param filePath     path to an *.iml file where module configuration will be saved; name of the module will be equal to the file name without extension.
   * @param moduleTypeId the ID of the module type to create.
   * @return the module instance.
   */
  public abstract @NotNull Module newModule(@NotNull @NonNls String filePath, @NotNull String moduleTypeId);

  public @NotNull Module newModule(@NotNull Path file, @NotNull String moduleTypeId) {
    return newModule(file.toString().replace(File.separatorChar, '/'), moduleTypeId);
  }

  /**
   * Creates a non-persistent module of the specified type and adds it to the project
   * to which the module manager is related. {@link #commit()} must be called to
   * bring the changes in effect.
   *
   * In contrast with modules created by {@link #newModule(String, String)},
   * non-persistent modules aren't stored on a filesystem and aren't being written
   * in a project XML file. When IDE closes, all non-persistent modules vanishes out.
   */
  @ApiStatus.Experimental
  @NotNull
  public Module newNonPersistentModule(@NotNull String moduleName, @NotNull String id) {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated Use {@link #loadModule(Path)}
   */
  @Deprecated
  public abstract @NotNull Module loadModule(@NotNull String filePath) throws IOException, JDOMException, ModuleWithNameAlreadyExists;

  /**
   * Loads a module from an .iml file with the specified path and adds it to the project.
   *
   * @param file the path to load the module from.
   * @return the module instance.
   * @throws IOException                 if an I/O error occurred when loading the module file.
   * @throws ModuleWithNameAlreadyExists if a module with such a name already exists in the project.
   */
  public abstract @NotNull Module loadModule(@NotNull Path file) throws IOException, ModuleWithNameAlreadyExists;

  /**
   * Disposes of the specified module and removes it from the project.
   *
   * @param module the module to remove.
   */
  public abstract void disposeModule(@NotNull Module module);

  /**
   * Returns the list of all modules in the project.
   *
   * @return the array of modules.
   */
  public abstract Module @NotNull [] getModules();

  /**
   * Returns the project module with the specified name.
   *
   * @param name the name of the module to find.
   * @return the module instance, or null if no module with such name exists.
   */
  @Nullable
  public abstract Module findModuleByName(@NonNls @NotNull String name);

  /**
   * Returns the list of modules sorted by dependency (the modules which do not depend
   * on anything are in the beginning of the list, a module which depends on another module
   * follows it in the list).
   *
   * @return the sorted array of modules.
   */
  public abstract Module @NotNull [] getSortedModules();

  /**
   * Returns the module comparator which can be used for sorting modules by dependency
   * (the modules which do not depend on anything are in the beginning of the list,
   * a module which depends on another module follows it in the list).
   *
   * @return the module comparator instance.
   */
  @NotNull
  public abstract Comparator<Module> moduleDependencyComparator();

  /**
   * Returns the list of modules which directly depend on the specified module.
   *
   * @param module the module for which the list of dependent modules is requested.
   * @return list of <i>modules that depend on</i> given module.
   * @see ModuleUtilCore#getAllDependentModules(Module)
   */
  @NotNull
  public abstract List<Module> getModuleDependentModules(@NotNull Module module);

  /**
   * Checks if one of the specified modules directly depends on the other module.
   *
   * @param module   the module to check the dependency for.
   * @param onModule the module on which {@code module} may depend.
   * @return true if {@code module} directly depends on {@code onModule}, false otherwise.
   */
  public abstract boolean isModuleDependent(@NotNull Module module, @NotNull Module onModule);

  /**
   * Returns the graph of dependencies between modules in the project.
   *
   * @return the module dependency graph.
   */
  @NotNull
  public abstract Graph<Module> moduleGraph();

  /**
   * Returns the graph of dependencies between modules in the project.
   *
   * @param includeTests whether test-only dependencies should be included
   * @return the module dependency graph.
   */
  @NotNull
  public abstract Graph<Module> moduleGraph(boolean includeTests);

  /**
   * Returns the model for the list of modules in the project, which can be used to add,
   * remove or modify modules.
   *
   * @return the modifiable model instance.
   */
  @NotNull
  public abstract ModifiableModuleModel getModifiableModel();


  /**
   * Returns the path to the group to which the specified module belongs, as an array of group names starting from the project root.
   * <p>
   * <strong>Use {@link ModuleGrouper#getGroupPath(Module)} instead.</strong> Explicit module groups will be replaced
   * by automatical module grouping accordingly to qualified names of modules, see https://youtrack.jetbrains.com/issue/IDEA-166061 for details.
   * </p>
   * @param module the module for which the path is requested.
   * @return the path to the group for the module, or null if the module does not belong to any group.
   */
  public abstract String @Nullable [] getModuleGroupPath(@NotNull Module module);

  public abstract boolean hasModuleGroups();

  /**
   * @return description of all modules in the project including unloaded
   */
  @ApiStatus.Experimental
  @NotNull
  public abstract Collection<ModuleDescription> getAllModuleDescriptions();

  @ApiStatus.Experimental
  @NotNull
  public abstract Collection<UnloadedModuleDescription> getUnloadedModuleDescriptions();

  @ApiStatus.Experimental
  @Nullable
  public abstract UnloadedModuleDescription getUnloadedModuleDescription(@NotNull String moduleName);

  @NotNull
  public abstract ModuleGrouper getModuleGrouper(@Nullable ModifiableModuleModel model);

  /**
   * Specify list of modules which will be unloaded from the project.
   * @see UnloadedModuleDescription
   */
  @ApiStatus.Experimental
  public abstract void setUnloadedModules(@NotNull List<String> unloadedModuleNames);

  @ApiStatus.Experimental
  public void removeUnloadedModules(@NotNull Collection<? extends UnloadedModuleDescription> unloadedModules) {
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for getting information about the contents and dependencies of a module.
 *
 * @see CompilerModuleExtension
 */
@ApiStatus.NonExtendable
public abstract class ModuleRootManager implements ModuleRootModel, ProjectModelElement {
  /**
   * Returns the module root manager instance for the specified module.
   *
   * @param module the module for which the root manager is requested.
   * @return the root manager instance.
   */
  public static ModuleRootManager getInstance(@NotNull Module module) {
    return module.getComponent(ModuleRootManager.class);
  }

  /**
   * Returns the file index for the current module.
   *
   * @return the file index instance.
   */
  public abstract @NotNull ModuleFileIndex getFileIndex();

  /**
   * Returns the interface for modifying the set of roots for this module. Must be called in a read action.
   * !!!!! WARNING !!!!!: This model MUST be either committed {@link ModifiableRootModel#commit()} or disposed {@link ModifiableRootModel#dispose()}
   *
   * @return the modifiable root model.
   */
  public abstract @NotNull ModifiableRootModel getModifiableModel();

  /**
   * Returns the list of modules on which the current module directly depends. The method does not traverse
   * the entire dependency structure - dependencies of dependency modules are not included in the returned list.
   *
   * @return the array of module direct dependencies.
   */
  public abstract Module @NotNull [] getDependencies();

  /**
   * Returns the list of modules on which the current module directly depends. The method does not traverse
   * the entire dependency structure - dependencies of dependency modules are not included in the returned list.
   *
   * @param includeTests whether test-only dependencies should be included
   * @return the array of module direct dependencies.
   */
  public abstract Module @NotNull [] getDependencies(boolean includeTests);

  /**
   * Checks if the current module directly depends on the specified module.
   *
   * @param module the module to check.
   * @return true if {@code module} is contained in the list of dependencies for the current module, false otherwise.
   */
  public abstract boolean isDependsOn(@NotNull Module module);
}
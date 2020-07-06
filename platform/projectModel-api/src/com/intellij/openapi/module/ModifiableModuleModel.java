// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Represents the model for the list of modules in a project, or a temporary copy
 * of that model displayed in the configuration UI.
 *
 * @see ModuleManager#getModifiableModel()
 */
@ApiStatus.NonExtendable
public interface ModifiableModuleModel {
  /**
   * Returns the list of all modules in the project. Same as {@link ModuleManager#getModules()}.
   *
   * @return the array of modules.
   */
  Module @NotNull [] getModules();

  @NotNull Module newModule(@NotNull String filePath, @NotNull String moduleTypeId);

  /**
   * Creates a module of the specified type at the specified path and adds it to the project
   * to which the module manager is related. {@link #commit()} must be called to
   * bring the changes in effect.
   *
   * @param file         path to an *.iml file where module configuration will be saved; name of the module will be equal to the file name without extension.
   * @param moduleTypeId the ID of the module type to create.
   * @return the module instance.
   */
  default @NotNull Module newModule(@NotNull Path file, @NotNull String moduleTypeId) {
    return newModule(file.toAbsolutePath().normalize().toString().replace(File.separatorChar, '/'), moduleTypeId);
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
  default Module newNonPersistentModule(@NotNull String moduleName, @NotNull String moduleTypeId) {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated use {@link #newModule(String, String)} instead
   */
  @Deprecated
  @NotNull
  Module newModule(@NotNull String filePath, @NotNull String moduleTypeId, @Nullable Map<String, String> options);

  /**
   * Loads a module from an .iml file with the specified path and adds it to the project.
   * {@link #commit()} must be called to bring the changes in effect.
   *
   * @param filePath the path to load the module from.
   * @return the module instance.
   * @throws IOException                 if an I/O error occurred when loading the module file.
   * @throws ModuleWithNameAlreadyExists if a module with such a name already exists in the project.
   */
  @NotNull
  Module loadModule(@NotNull @SystemIndependent String filePath) throws IOException, ModuleWithNameAlreadyExists;

  Module loadModule(@NotNull Path file) throws IOException;

  /**
   * Disposes of the specified module and removes it from the project. {@link #commit()}
   * must be called to bring the changes in effect.
   *
   * @param module the module to remove.
   */
  void disposeModule(@NotNull Module module);

  /**
   * Returns the project module with the specified name.
   *
   * @param name the name of the module to find.
   * @return the module instance, or null if no module with such name exists.
   */
  @Nullable
  Module findModuleByName(@NotNull String name);

  /**
   * Disposes of all modules in the project.
   */
  void dispose();

  /**
   * Checks if there are any uncommitted changes to the model.
   *
   * @return true if there are uncommitted changes, false otherwise
   */
  boolean isChanged();

  /**
   * Commits changes made in this model to the actual project structure.
   */
  void commit();

  /**
   * Schedules the rename of a module to be performed when the model is committed.
   *
   * @param module  the module to rename.
   * @param newName the new name to rename the module to.
   * @throws ModuleWithNameAlreadyExists if a module with such a name already exists in the project.
   */
  void renameModule(@NotNull Module module, @NotNull String newName) throws ModuleWithNameAlreadyExists;

  /**
   * Returns the project module which has been renamed to the specified name.
   *
   * @param newName the name of the renamed module to find.
   * @return the module instance, or null if no module has been renamed to such a name.
   */
  @Nullable
  Module getModuleToBeRenamed(@NotNull String newName);

  /**
   * Returns the name to which the specified module has been renamed.
   *
   * @param module the module for which the new name is requested.
   * @return the new name, or null if the module has not been renamed.
   */
  @Nullable
  String getNewName(@NotNull Module module);

  /**
   * @return the new name of {@code module} if it has been renamed or its old name it hasn't.
   */
  @NotNull
  String getActualName(@NotNull Module module);

  String @Nullable [] getModuleGroupPath(@NotNull Module module);

  boolean hasModuleGroups();

  void setModuleGroupPath(@NotNull Module module, String @Nullable("null means remove") [] groupPath);

  @NotNull
  Project getProject();
}

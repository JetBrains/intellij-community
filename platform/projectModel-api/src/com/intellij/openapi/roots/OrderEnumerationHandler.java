// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Implement this extension to change how dependencies of modules are processed by the IDE. You may also need to register implementation of
 * {@link org.jetbrains.jps.model.java.impl.JpsJavaDependenciesEnumerationHandler} extension to ensure that the same logic applies inside
 * JPS build process.
 */
@ApiStatus.OverrideOnly
public abstract class OrderEnumerationHandler {
  public static final ExtensionPointName<Factory> EP_NAME =
    ExtensionPointName.create("com.intellij.orderEnumerationHandlerFactory");

  public abstract static class Factory {
    @Contract(pure = true)
    public abstract boolean isApplicable(@NotNull Module module);

    @Contract(pure = true)
    public abstract @NotNull OrderEnumerationHandler createHandler(@NotNull Module module);
  }

  public enum AddDependencyType {ADD, DO_NOT_ADD, DEFAULT}

  public @NotNull AddDependencyType shouldAddDependency(@NotNull OrderEntry orderEntry,
                                                        @NotNull OrderEnumeratorSettings settings) {
    return AddDependencyType.DEFAULT;
  }

  public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath() {
    return false;
  }

  public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
    return true;
  }

  public boolean shouldProcessDependenciesRecursively() {
    return true;
  }

  /**
   * Returns {@code true} if resource files located under roots of types {@link org.jetbrains.jps.model.java.JavaModuleSourceRootTypes#SOURCES}
   * are copied to the module output.
   */
  public boolean areResourceFilesFromSourceRootsCopiedToOutput() {
    return true;
  }

  /**
   * Override this method to contribute custom roots for a library or SDK instead of the configured ones.
   * @return {@code false} if no customization was performed, and therefore the default roots should be added.
   */
  public boolean addCustomRootsForLibraryOrSdk(@NotNull LibraryOrSdkOrderEntry forOrderEntry,
                                               @NotNull OrderRootType type,
                                               @NotNull Collection<String> urls) {
    return false;
  }

  public boolean addCustomModuleRoots(@NotNull OrderRootType type,
                                      @NotNull ModuleRootModel rootModel,
                                      @NotNull Collection<String> result,
                                      boolean includeProduction,
                                      boolean includeTests) {
    return false;
  }
}

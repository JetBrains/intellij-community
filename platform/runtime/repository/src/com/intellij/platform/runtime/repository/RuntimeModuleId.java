// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represent an ID of a runtime module descriptor. It's guaranteed that there are no two {@link RuntimeModuleDescriptor modules} with equal
 * IDs in the same {@link RuntimeModuleRepository repository}.
 * <p>
 * In the future, this class should be probably merged with {@link com.intellij.ide.plugins.PluginModuleId}.
 */
public final class RuntimeModuleId {
  @ApiStatus.Internal
  public static final String DEFAULT_NAMESPACE = "jetbrains";
  @ApiStatus.Internal
  public static final String LIB_NAME_PREFIX = "lib.";
  @ApiStatus.Internal
  public static final String TESTS_NAME_SUFFIX = ".tests";
  private final String myName;
  private final String myNamespace;

  private RuntimeModuleId(@NotNull String name, @NotNull String namespace) {
    myName = name;
    myNamespace = namespace;
  }

  /**
   * Returns a string representation of the ID.  
   */
  public @NotNull String getStringId() {
    return myName;
  }

  public @NotNull String getName() {
    return myName;
  }

  public @NotNull String getNamespace() {
    return myNamespace;
  }

  /**
   * Returns a human-readable name of the module. It can be used for debugging and logging purposes only.
   */
  public @NotNull String getPresentableName() {
    return myNamespace.equals(DEFAULT_NAMESPACE) ? myName : myNamespace + ":" + myName;
  }

  /**
   * Creates ID from a raw string representation as it's written in the runtime module repository.
   * This method is supposed to be used to generate and transform the module repository only, other code should use other methods.
   */
  @ApiStatus.Internal
  public static @NotNull RuntimeModuleId raw(@NotNull String stringId) {
    return new RuntimeModuleId(stringId, DEFAULT_NAMESPACE);
  }

  /**
   * Creates ID from a raw string representation as it's written in the runtime module repository.
   * This method is supposed to be used to generate and transform the module repository only, other code should use other methods.
   */
  @ApiStatus.Internal
  public static @NotNull RuntimeModuleId raw(@NotNull String moduleName, @NotNull String namespace) {
    return new RuntimeModuleId(moduleName, namespace);
  }

  /**
   * Creates ID of a runtime module corresponding to the production part of module {@code moduleName} in intellij project configuration.
   */
  public static @NotNull RuntimeModuleId module(@NotNull String moduleName) {
    return new RuntimeModuleId(moduleName, DEFAULT_NAMESPACE);
  }

  /**
   * Creates ID of a runtime module corresponding to a plugin content module with the name {@code moduleName} in the namespace {@code namespace}.
   */
  public static @NotNull RuntimeModuleId contentModule(@NotNull String moduleName, @NotNull String namespace) {
    return new RuntimeModuleId(moduleName, namespace);
  }

  /**
   * Creates ID of a runtime module corresponding to the test part of module {@code moduleName} in intellij project configuration.
   */
  public static @NotNull RuntimeModuleId moduleTests(@NotNull String moduleName) {
    return new RuntimeModuleId(moduleName + TESTS_NAME_SUFFIX, DEFAULT_NAMESPACE);
  }

  /**
   * Creates ID of a runtime module corresponding to the project-level library {@code libraryName} in intellij project configuration.
   */
  public static @NotNull RuntimeModuleId projectLibrary(@NotNull String libraryName) {
    return new RuntimeModuleId(LIB_NAME_PREFIX + libraryName, DEFAULT_NAMESPACE);
  }

  /**
   * @deprecated module-level libraries are now merged with corresponding modules at runtime, it doesn't make sense to have separate IDs for
   * them.
   */
  @Deprecated(forRemoval = true)
  public static @NotNull RuntimeModuleId moduleLibrary(@NotNull String moduleName, @NotNull String libraryName) {
    return new RuntimeModuleId(LIB_NAME_PREFIX + moduleName + "." + libraryName, DEFAULT_NAMESPACE);
  }

  @Override
  public String toString() {
    return "RuntimeModuleId{name=" + myName + ", namespace=" + myNamespace + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return myName.equals(((RuntimeModuleId)o).myName);
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }
}

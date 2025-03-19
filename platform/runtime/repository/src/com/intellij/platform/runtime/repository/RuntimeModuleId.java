// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represent an ID of a runtime module descriptor. It's guaranteed that there are no two {@link RuntimeModuleDescriptor modules} with equal
 * IDs in the same {@link RuntimeModuleRepository repository}.
 */
public final class RuntimeModuleId {
  @ApiStatus.Internal
  public static final String LIB_NAME_PREFIX = "lib.";
  @ApiStatus.Internal
  public static final String TESTS_NAME_SUFFIX = ".tests";
  private final String myStringId;

  private RuntimeModuleId(@NotNull String stringId) {
    myStringId = stringId;
  }

  /**
   * Returns a string representation of the ID.  
   */
  public @NotNull String getStringId() {
    return myStringId;
  }

  /**
   * Creates ID from a raw string representation as it's written in the runtime module repository. 
   * This method is supposed to be used to generate and transform the module repository only, other code should use other methods.
   */
  @ApiStatus.Internal
  public static @NotNull RuntimeModuleId raw(@NotNull String stringId) {
    return new RuntimeModuleId(stringId);
  }

  /**
   * Creates ID of a runtime module corresponding to the production part of module {@code moduleName} in intellij project configuration.
   */
  public static @NotNull RuntimeModuleId module(@NotNull String moduleName) {
    return new RuntimeModuleId(moduleName);
  }

  /**
   * Creates ID of a runtime module corresponding to the test part of module {@code moduleName} in intellij project configuration.
   */
  public static @NotNull RuntimeModuleId moduleTests(@NotNull String moduleName) {
    return new RuntimeModuleId(moduleName.equals("intellij.platform.split")
                               ? "intellij.platform.split.testFramework"
                               : moduleName + TESTS_NAME_SUFFIX);
  }

  /**
   * Creates ID of a runtime module corresponding to the project-level library {@code libraryName} in intellij project configuration.
   */
  public static @NotNull RuntimeModuleId projectLibrary(@NotNull String libraryName) {
    return new RuntimeModuleId(LIB_NAME_PREFIX + libraryName);
  }

  /**
   * Creates ID of a runtime module corresponding to the module-level library {@code libraryName} from module {@code moduleName} in intellij 
   * project configuration.
   */
  public static @NotNull RuntimeModuleId moduleLibrary(@NotNull String moduleName, @NotNull String libraryName) {
    return new RuntimeModuleId(LIB_NAME_PREFIX + moduleName + "." + libraryName);
  }

  @Override
  public String toString() {
    return "RuntimeModuleId[" + myStringId + "]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return myStringId.equals(((RuntimeModuleId)o).myStringId);
  }

  @Override
  public int hashCode() {
    return myStringId.hashCode();
  }
}

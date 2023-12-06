// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represent an ID of a runtime module descriptor. It's guaranteed that there are no two {@link RuntimeModuleDescriptor modules} with equal
 * IDs in the same {@link RuntimeModuleRepository repository}.
 */
public final class RuntimeModuleId {
  public static final String LIB_NAME_PREFIX = "lib.";
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

  @ApiStatus.Internal
  public static @NotNull RuntimeModuleId raw(@NotNull String stringId) {
    return new RuntimeModuleId(stringId);
  }
  
  public static @NotNull RuntimeModuleId module(@NotNull String moduleName) {
    return new RuntimeModuleId(moduleName);
  }

  public static @NotNull RuntimeModuleId moduleTests(@NotNull String moduleName) {
    return new RuntimeModuleId(moduleName + TESTS_NAME_SUFFIX);
  }

  public static @NotNull RuntimeModuleId projectLibrary(@NotNull String libraryName) {
    return new RuntimeModuleId(LIB_NAME_PREFIX + libraryName);
  }

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

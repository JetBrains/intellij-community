package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/10/11 6:32 PM
 */
public interface GradleLibraryDependency extends GradleDependency {

  @NotNull
  String getName();
  
  /**
   * Allows to ask for the target path configured for the current library dependency.
   * 
   * @param type  target path type
   * @return      path to the target path configured for the current library dependency
   */
  @Nullable
  String getPath(@NotNull LibraryPathType type);
}

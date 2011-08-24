package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/24/11 4:49 PM
 */
public interface GradleLibrary extends Named, GradleEntity {

  /**
   * Allows to ask for the target path configured for the current library dependency.
   *
   * @param type  target path type
   * @return      path to the target path configured for the current library dependency
   */
  @Nullable
  String getPath(@NotNull LibraryPathType type);
}

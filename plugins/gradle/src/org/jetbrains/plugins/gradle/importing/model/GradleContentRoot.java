package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/9/11 6:39 PM
 */
public interface GradleContentRoot extends GradleEntity {

  @NotNull
  String getRootPath();

  /**
   * @param type      target dir type
   * @return          directories of the target type configured for the current content root
   */
  @NotNull
  Collection<String> getPaths(@NotNull SourceType type);
}

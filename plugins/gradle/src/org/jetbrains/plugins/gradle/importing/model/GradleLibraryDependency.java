package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;

/**
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/10/11 6:32 PM
 */
public interface GradleLibraryDependency extends GradleDependency {

  @NotNull
  GradleLibrary getLibrary();
}

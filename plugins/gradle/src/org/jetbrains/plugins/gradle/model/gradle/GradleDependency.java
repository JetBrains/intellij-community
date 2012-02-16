package org.jetbrains.plugins.gradle.model.gradle;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

/**
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/10/11 6:31 PM
 */
public interface GradleDependency extends GradleEntity {
  
  boolean isExported();

  @NotNull
  DependencyScope getScope();

  @NotNull
  GradleModule getOwnerModule();
  
  @NotNull
  GradleDependency clone(@NotNull GradleEntityCloneContext context);
}

package org.jetbrains.plugins.gradle.importing.model;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

/**
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/10/11 6:31 PM
 */
public interface GradleDependency {
  
  boolean isExported();

  @NotNull
  DependencyScope getScope();
  
  void invite(@NotNull GradleDependencyVisitor visitor);
}

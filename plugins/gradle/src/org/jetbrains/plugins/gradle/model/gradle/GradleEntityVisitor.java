package org.jetbrains.plugins.gradle.model.gradle;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/12/11 12:49 PM
 */
public interface GradleEntityVisitor {

  void visit(@NotNull GradleProject project);
  void visit(@NotNull GradleModule module);
  void visit(@NotNull GradleContentRoot contentRoot);
  void visit(@NotNull GradleLibrary library);
  void visit(@NotNull GradleJar jar);
  void visit(@NotNull GradleModuleDependency dependency);
  void visit(@NotNull GradleLibraryDependency dependency);
}

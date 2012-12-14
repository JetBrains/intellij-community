package org.jetbrains.plugins.gradle.model.gradle;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/23/11 1:16 PM
 */
public abstract class GradleEntityVisitorAdapter implements GradleEntityVisitor {
  @Override
  public void visit(@NotNull GradleProject project) {
  }

  @Override
  public void visit(@NotNull GradleModule module) {
  }

  @Override
  public void visit(@NotNull GradleContentRoot contentRoot) {
  }

  @Override
  public void visit(@NotNull GradleLibrary library) {
  }

  @Override
  public void visit(@NotNull GradleJar jar) {
  }

  @Override
  public void visit(@NotNull GradleModuleDependency dependency) {
  }

  @Override
  public void visit(@NotNull GradleLibraryDependency dependency) {
  }
}

package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:31 PM
 */
public interface GradleDependencyVisitor {
  void visit(@NotNull GradleModuleDependency dependency);
  void visit(@NotNull GradleLibraryDependency dependency);
}

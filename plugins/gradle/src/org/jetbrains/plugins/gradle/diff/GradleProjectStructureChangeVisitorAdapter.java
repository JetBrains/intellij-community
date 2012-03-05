package org.jetbrains.plugins.gradle.diff;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 3/2/12 4:21 PM
 */
public class GradleProjectStructureChangeVisitorAdapter implements GradleProjectStructureChangeVisitor {
  @Override
  public void visit(@NotNull GradleProjectRenameChange change) {
  }

  @Override
  public void visit(@NotNull GradleLanguageLevelChange change) {
  }

  @Override
  public void visit(@NotNull GradleModulePresenceChange change) {
  }

  @Override
  public void visit(@NotNull GradleContentRootPresenceChange change) {
  }

  @Override
  public void visit(@NotNull GradleLibraryDependencyPresenceChange change) {
  }

  @Override
  public void visit(@NotNull GradleModuleDependencyPresenceChange change) {
  }

  @Override
  public void visit(@NotNull GradleMismatchedLibraryPathChange change) {
  }
}

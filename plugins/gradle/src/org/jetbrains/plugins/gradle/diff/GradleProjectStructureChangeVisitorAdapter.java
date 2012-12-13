package org.jetbrains.plugins.gradle.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.contentroot.GradleContentRootPresenceChange;
import org.jetbrains.plugins.gradle.diff.dependency.*;
import org.jetbrains.plugins.gradle.diff.library.GradleJarPresenceChange;
import org.jetbrains.plugins.gradle.diff.module.GradleModulePresenceChange;
import org.jetbrains.plugins.gradle.diff.project.GradleLanguageLevelChange;
import org.jetbrains.plugins.gradle.diff.project.GradleProjectRenameChange;

/**
 * @author Denis Zhdanov
 * @since 3/2/12 4:21 PM
 */
public abstract class GradleProjectStructureChangeVisitorAdapter implements GradleProjectStructureChangeVisitor {
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
  public void visit(@NotNull GradleJarPresenceChange change) {
  }

  @Override
  public void visit(@NotNull GradleModuleDependencyPresenceChange change) {
  }

  @Override
  public void visit(@NotNull GradleDependencyScopeChange change) {
  }

  @Override
  public void visit(@NotNull GradleDependencyExportedChange change) {
  }
}

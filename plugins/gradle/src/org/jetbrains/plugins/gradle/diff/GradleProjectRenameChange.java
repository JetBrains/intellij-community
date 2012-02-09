package org.jetbrains.plugins.gradle.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleBundle;

/**
 * Describes project name change.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:54 PM
 */
public class GradleProjectRenameChange extends GradleAbstractConflictingPropertyChange<String> {

  public GradleProjectRenameChange(@NotNull String gradleName, @NotNull String intellijName) {
    super(GradleBundle.message("gradle.sync.change.project.name.text"), gradleName, intellijName);
  }

  @Override
  public void invite(@NotNull GradleProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }
}

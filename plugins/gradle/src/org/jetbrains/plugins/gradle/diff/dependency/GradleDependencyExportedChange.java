package org.jetbrains.plugins.gradle.diff.dependency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.GradleAbstractConflictingPropertyChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.model.id.GradleAbstractDependencyId;
import org.jetbrains.plugins.gradle.util.GradleBundle;

/**
 * @author Denis Zhdanov
 * @since 3/14/12 1:34 PM
 */
public class GradleDependencyExportedChange extends GradleAbstractConflictingPropertyChange<Boolean> {

  public GradleDependencyExportedChange(@NotNull GradleAbstractDependencyId id,
                                        boolean gradleValue,
                                        boolean intellijValue)
  {
    super(id, GradleBundle.message("gradle.sync.change.dependency.exported", id), gradleValue, intellijValue);
  }

  @Override
  public void invite(@NotNull GradleProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }
}

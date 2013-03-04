package org.jetbrains.plugins.gradle.diff.dependency;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.AbstractGradleConflictingPropertyChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.model.id.AbstractGradleDependencyId;
import org.jetbrains.plugins.gradle.util.GradleBundle;

/**
 * @author Denis Zhdanov
 * @since 3/12/12 6:02 PM
 */
public class GradleDependencyScopeChange extends AbstractGradleConflictingPropertyChange<DependencyScope> {
  
  public GradleDependencyScopeChange(@NotNull AbstractGradleDependencyId id,
                                     @NotNull DependencyScope gradleValue,
                                     @NotNull DependencyScope intellijValue)
  {
    super(id, GradleBundle.message("gradle.sync.change.dependency.scope", id), gradleValue, intellijValue);
  }

  @NotNull
  @Override
  public AbstractGradleDependencyId getEntityId() {
    return (AbstractGradleDependencyId)super.getEntityId();
  }

  @Override
  public void invite(@NotNull GradleProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }
}

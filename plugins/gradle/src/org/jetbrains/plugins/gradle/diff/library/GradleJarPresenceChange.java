package org.jetbrains.plugins.gradle.diff.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.diff.AbstractGradleEntityPresenceChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.model.id.GradleJarId;
import org.jetbrains.plugins.gradle.util.GradleBundle;

/**
 * @author Denis Zhdanov
 * @since 12/11/12 7:52 PM
 */
public class GradleJarPresenceChange extends AbstractGradleEntityPresenceChange<GradleJarId> {

  public GradleJarPresenceChange(@Nullable GradleJarId gradleEntity,
                                 @Nullable GradleJarId ideEntity) throws IllegalArgumentException
  {
    super(GradleBundle.message("gradle.sync.change.entity.type.jar"), gradleEntity, ideEntity);
  }

  @Override
  public void invite(@NotNull GradleProjectStructureChangeVisitor visitor) {
    visitor.visit(this); 
  }
}

package org.jetbrains.plugins.gradle.diff.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.diff.GradleAbstractEntityPresenceChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.model.id.GradleJarId;
import org.jetbrains.plugins.gradle.util.GradleBundle;

/**
 * @author Denis Zhdanov
 * @since 12/11/12 7:52 PM
 */
public class GradleJarPresenceChange extends GradleAbstractEntityPresenceChange<GradleJarId> {

  public GradleJarPresenceChange(@Nullable GradleJarId gradleEntity,
                                 @Nullable GradleJarId intellijEntity) throws IllegalArgumentException
  {
    super(GradleBundle.message("gradle.sync.change.entity.type.jar"), gradleEntity, intellijEntity);
  }

  @Override
  public void invite(@NotNull GradleProjectStructureChangeVisitor visitor) {
    visitor.visit(this); 
  }
}

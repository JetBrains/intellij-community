package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.roots.LibraryOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.GradleLibraryDependency;

/**
 * @author Denis Zhdanov
 * @since 1/24/12 9:48 AM
 */
public class GradleLibraryDependencyPresenceChange extends GradleEntityPresenceChange<GradleLibraryDependency, LibraryOrderEntry> {

  public GradleLibraryDependencyPresenceChange(@Nullable GradleLibraryDependency gradleEntity, @Nullable LibraryOrderEntry intellijEntity)
    throws IllegalArgumentException
  {
    super(gradleEntity, intellijEntity);
  }

  @Override
  public void invite(@NotNull GradleProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }
}

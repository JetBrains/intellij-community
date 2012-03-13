package org.jetbrains.plugins.gradle.diff.dependency;

import com.intellij.openapi.roots.LibraryOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.diff.GradleAbstractEntityPresenceChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibraryDependency;
import org.jetbrains.plugins.gradle.model.id.GradleEntityIdMapper;
import org.jetbrains.plugins.gradle.model.id.GradleLibraryDependencyId;
import org.jetbrains.plugins.gradle.util.GradleBundle;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/24/12 9:48 AM
 */
public class GradleLibraryDependencyPresenceChange extends GradleAbstractEntityPresenceChange<GradleLibraryDependencyId> {

  public GradleLibraryDependencyPresenceChange(@Nullable GradleLibraryDependency gradleDependency,
                                               @Nullable LibraryOrderEntry intellijDependency) throws IllegalArgumentException
  {
    super(GradleBundle.message("gradle.sync.change.entity.type.library.dependency"), of(gradleDependency), of(intellijDependency));
  }

  @Override
  public void invite(@NotNull GradleProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }

  @Nullable
  private static GradleLibraryDependencyId of(@Nullable Object dependency) {
    if (dependency == null) {
      return null;
    }
    return GradleEntityIdMapper.mapEntityToId(dependency);
  }
}

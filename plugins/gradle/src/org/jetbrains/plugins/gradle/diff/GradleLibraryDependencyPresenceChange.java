package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.roots.LibraryOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.GradleLibraryDependency;
import org.jetbrains.plugins.gradle.model.GradleLibraryDependencyId;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import static org.jetbrains.plugins.gradle.model.GradleLibraryDependencyId.of;

/**
 * @author Denis Zhdanov
 * @since 1/24/12 9:48 AM
 */
public class GradleLibraryDependencyPresenceChange extends GradleEntityPresenceChange<GradleLibraryDependencyId> {

  public GradleLibraryDependencyPresenceChange(@Nullable GradleLibraryDependency gradleDependency,
                                               @Nullable LibraryOrderEntry intellijDependency) throws IllegalArgumentException
  {
    super(GradleBundle.message("gradle.sync.change.entity.type.library.dependency"), of(gradleDependency), of(intellijDependency));
  }

  @Override
  public void invite(@NotNull GradleProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }
}

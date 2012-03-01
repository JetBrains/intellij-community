package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.roots.ModuleOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.gradle.GradleModuleDependency;
import org.jetbrains.plugins.gradle.model.id.GradleEntityIdMapper;
import org.jetbrains.plugins.gradle.model.id.GradleModuleDependencyId;
import org.jetbrains.plugins.gradle.util.GradleBundle;

/**
 * @author Denis Zhdanov
 * @since 2/17/12 3:14 PM
 */
public class GradleModuleDependencyPresenceChange extends GradleEntityPresenceChange<GradleModuleDependencyId> {

  public GradleModuleDependencyPresenceChange(@Nullable GradleModuleDependency gradle,
                                              @Nullable ModuleOrderEntry intellij)
  {
    super(GradleBundle.message("gradle.sync.change.entity.type.module.dependency"), of(gradle), of(intellij));
  }

  @Override
  public void invite(@NotNull GradleProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }

  @Nullable
  private static GradleModuleDependencyId of(@Nullable Object dependency) {
    if (dependency == null) {
      return null;
    }
    return GradleEntityIdMapper.mapEntityToId(dependency);
  }
}

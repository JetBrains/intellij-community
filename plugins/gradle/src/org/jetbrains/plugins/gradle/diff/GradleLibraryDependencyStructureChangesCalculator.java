package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.roots.LibraryOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleLibraryDependency;

import java.util.Collections;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 1/24/12 1:37 PM
 */
public class GradleLibraryDependencyStructureChangesCalculator
  implements GradleStructureChangesCalculator<GradleLibraryDependency, LibraryOrderEntry>
{
  @NotNull
  @Override
  public Set<GradleProjectStructureChange> calculate(@NotNull GradleLibraryDependency gradleEntity,
                                                     @NotNull LibraryOrderEntry intellijEntity,
                                                     @NotNull Set<GradleProjectStructureChange> knownChanges)
  {
    // TODO den implement 
    return Collections.emptySet();
  }

  @NotNull
  @Override
  public Object getIntellijKey(@NotNull LibraryOrderEntry entity, @NotNull Set<GradleProjectStructureChange> knownChanges) {
    // TODO den consider the known changes
    final String result = entity.getLibraryName();
    return result == null ? "" : result;
  }

  @NotNull
  @Override
  public Object getGradleKey(@NotNull GradleLibraryDependency entity, @NotNull Set<GradleProjectStructureChange> knownChanges) {
    // TODO den consider the known changes
    return entity.getName();
  }
}

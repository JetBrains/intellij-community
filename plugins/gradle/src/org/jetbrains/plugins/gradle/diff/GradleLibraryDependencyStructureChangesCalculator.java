package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibraryDependency;

import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 1/24/12 1:37 PM
 */
public class GradleLibraryDependencyStructureChangesCalculator
  implements GradleStructureChangesCalculator<GradleLibraryDependency, LibraryOrderEntry>
{
  
  private final GradleLibraryStructureChangesCalculator myLibraryCalculator;

  public GradleLibraryDependencyStructureChangesCalculator(@NotNull GradleLibraryStructureChangesCalculator libraryCalculator) {
    myLibraryCalculator = libraryCalculator;
  }

  @Override
  public void calculate(@NotNull GradleLibraryDependency gradleEntity,
                        @NotNull LibraryOrderEntry intellijEntity,
                        @NotNull Set<GradleProjectStructureChange> knownChanges,
                        @NotNull Set<GradleProjectStructureChange> currentChanges)
  {
    final Library library = intellijEntity.getLibrary();
    if (library == null) {
      return;
    }
    myLibraryCalculator.calculate(gradleEntity.getTarget(), library, knownChanges, currentChanges);
  }

  @NotNull
  @Override
  public Object getIntellijKey(@NotNull LibraryOrderEntry entity) {
    final String result = entity.getLibraryName();
    return result == null ? "" : result;
  }

  @NotNull
  @Override
  public Object getGradleKey(@NotNull GradleLibraryDependency entity, @NotNull Set<GradleProjectStructureChange> knownChanges) {
    return myLibraryCalculator.getGradleKey(entity.getTarget(), knownChanges);
  }
}

package org.jetbrains.plugins.gradle.diff.dependency;

import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.GradleChangesCalculationContext;
import org.jetbrains.plugins.gradle.diff.library.GradleLibraryStructureChangesCalculator;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibraryDependency;

/**
 * @author Denis Zhdanov
 * @since 1/24/12 1:37 PM
 */
public class GradleLibraryDependencyStructureChangesCalculator
  extends GradleAbstractDependencyStructureChangesCalculator<GradleLibraryDependency, LibraryOrderEntry>
{
  
  private final GradleLibraryStructureChangesCalculator myLibraryCalculator;

  public GradleLibraryDependencyStructureChangesCalculator(@NotNull GradleLibraryStructureChangesCalculator libraryCalculator) {
    myLibraryCalculator = libraryCalculator;
  }

  @Override
  public void doCalculate(@NotNull GradleLibraryDependency gradleEntity,
                          @NotNull LibraryOrderEntry intellijEntity,
                          @NotNull GradleChangesCalculationContext context)
  {
    final Library library = intellijEntity.getLibrary();
    if (library == null) {
      return;
    }
    myLibraryCalculator.calculate(gradleEntity.getTarget(), library, context);
  }

  @NotNull
  @Override
  public Object getIntellijKey(@NotNull LibraryOrderEntry entity) {
    final Library library = entity.getLibrary();
    if (library == null) {
      return "";
    }
    return myLibraryCalculator.getIntellijKey(library);
  }

  @NotNull
  @Override
  public Object getGradleKey(@NotNull GradleLibraryDependency entity, @NotNull GradleChangesCalculationContext context) {
    return myLibraryCalculator.getGradleKey(entity.getTarget(), context);
  }
}

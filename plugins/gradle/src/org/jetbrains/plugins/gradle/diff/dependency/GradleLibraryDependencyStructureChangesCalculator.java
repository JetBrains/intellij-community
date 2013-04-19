package org.jetbrains.plugins.gradle.diff.dependency;

import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.service.project.change.ExternalProjectChangesCalculationContext;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.library.GradleLibraryStructureChangesCalculator;

/**
 * @author Denis Zhdanov
 * @since 1/24/12 1:37 PM
 */
public class GradleLibraryDependencyStructureChangesCalculator
  extends AbstractGradleDependencyStructureChangesCalculator<LibraryDependencyData, LibraryOrderEntry>
{
  
  private final GradleLibraryStructureChangesCalculator myLibraryCalculator;

  public GradleLibraryDependencyStructureChangesCalculator(@NotNull GradleLibraryStructureChangesCalculator libraryCalculator) {
    myLibraryCalculator = libraryCalculator;
  }

  @Override
  public void doCalculate(@NotNull LibraryDependencyData gradleEntity,
                          @NotNull LibraryOrderEntry intellijEntity,
                          @NotNull ExternalProjectChangesCalculationContext context)
  {
    final Library library = intellijEntity.getLibrary();
    if (library == null) {
      return;
    }
    myLibraryCalculator.calculate(gradleEntity.getTarget(), library, context);
  }

  // TODO den implement
//  @NotNull
//  @Override
//  public Object getIdeKey(@NotNull LibraryOrderEntry entity) {
//    final Library library = entity.getLibrary();
//    if (library == null) {
//      return "";
//    }
//    return myLibraryCalculator.getIdeKey(library);
//  }
//
//  @NotNull
//  @Override
//  public Object getGradleKey(@NotNull LibraryDependencyData entity, @NotNull ExternalProjectChangesCalculationContext context) {
//    return myLibraryCalculator.getGradleKey(entity.getTarget(), context);
//  }
}

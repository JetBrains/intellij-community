package org.jetbrains.plugins.gradle.diff.project;

import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChange;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChangesCalculator;
import com.intellij.openapi.externalSystem.model.project.change.LanguageLevelChange;
import com.intellij.openapi.externalSystem.model.project.change.GradleProjectRenameChange;
import com.intellij.openapi.externalSystem.service.project.change.ExternalProjectChangesCalculationContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import org.jetbrains.plugins.gradle.diff.GradleDiffUtil;
import org.jetbrains.plugins.gradle.diff.library.GradleLibraryStructureChangesCalculator;
import org.jetbrains.plugins.gradle.diff.module.GradleModuleStructureChangesCalculator;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * Encapsulates functionality of calculating changes between Gradle and IntelliJ IDEA project hierarchies.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:55 PM
 */
public class GradleProjectStructureChangesCalculator implements ExternalProjectStructureChangesCalculator<ProjectData, Project> {

  @NotNull private final GradleModuleStructureChangesCalculator myModuleChangesCalculator;
  @NotNull private final GradleLibraryStructureChangesCalculator myLibraryChangesCalculator;
  @NotNull private final PlatformFacade myPlatformFacade;

  public GradleProjectStructureChangesCalculator(@NotNull GradleModuleStructureChangesCalculator moduleCalculator,
                                                 @NotNull GradleLibraryStructureChangesCalculator calculator,
                                                 @NotNull PlatformFacade platformFacade)
  {
    myModuleChangesCalculator = moduleCalculator;
    myLibraryChangesCalculator = calculator;
    myPlatformFacade = platformFacade;
  }

  @Override
  public void calculate(@NotNull ProjectData gradleEntity,
                        @NotNull Project ideEntity,
                        @NotNull ExternalProjectChangesCalculationContext context)
  {
    calculateProjectChanges(gradleEntity, ideEntity, context.getCurrentChanges());
    
    // TODO den implement
//    final Set<? extends ModuleData> gradleSubEntities = gradleEntity.getModules();
//    final Collection<Module> intellijSubEntities = myPlatformFacade.getModules(ideEntity);
//    GradleDiffUtil.calculate(myModuleChangesCalculator, gradleSubEntities, intellijSubEntities, context);
//    
//    LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(ideEntity);
//    GradleDiffUtil.calculate(myLibraryChangesCalculator, gradleEntity.getLibraries(), Arrays.asList(libraryTable.getLibraries()), context);
  }

  @NotNull
  public Object getIdeKey(@NotNull Project entity) {
    return entity.getName();
  }

  @NotNull
  public Object getGradleKey(@NotNull ProjectData entity, @NotNull ExternalProjectChangesCalculationContext context) {
    return entity.getName();
  }

  private void calculateProjectChanges(@NotNull ProjectData gradleProject,
                                       @NotNull Project intellijProject,
                                       @NotNull Set<ExternalProjectStructureChange> currentChanges)
  {
    checkName(gradleProject, intellijProject, currentChanges);
    checkLanguageLevel(gradleProject, intellijProject, currentChanges);
  }

  private static void checkName(@NotNull ProjectData gradleProject,
                                @NotNull Project intellijProject,
                                @NotNull Set<ExternalProjectStructureChange> currentChanges)
  {
    String gradleName = gradleProject.getName();
    String intellijName = intellijProject.getName();
    if (!gradleName.equals(intellijName)) {
      currentChanges.add(new GradleProjectRenameChange(gradleName, intellijName));
    }
  }

  private void checkLanguageLevel(@NotNull ProjectData gradleProject,
                                  @NotNull Project intellijProject,
                                  @NotNull Set<ExternalProjectStructureChange> currentChanges)
  {
    // TODO den implement
//    LanguageLevel gradleLevel = gradleProject.getLanguageLevel();
//    LanguageLevel intellijLevel = myPlatformFacade.getLanguageLevel(intellijProject);
//    if (gradleLevel != intellijLevel) {
//      currentChanges.add(new LanguageLevelChange(gradleLevel, intellijLevel));
//    }
  }
}

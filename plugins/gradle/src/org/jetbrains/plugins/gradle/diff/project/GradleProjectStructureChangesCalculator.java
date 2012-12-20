package org.jetbrains.plugins.gradle.diff.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.PlatformFacade;
import org.jetbrains.plugins.gradle.diff.GradleChangesCalculationContext;
import org.jetbrains.plugins.gradle.diff.GradleDiffUtil;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.diff.GradleStructureChangesCalculator;
import org.jetbrains.plugins.gradle.diff.library.GradleLibraryStructureChangesCalculator;
import org.jetbrains.plugins.gradle.diff.module.GradleModuleStructureChangesCalculator;
import org.jetbrains.plugins.gradle.model.gradle.GradleModule;
import org.jetbrains.plugins.gradle.model.gradle.GradleProject;

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
public class GradleProjectStructureChangesCalculator implements GradleStructureChangesCalculator<GradleProject, Project> {

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
  public void calculate(@NotNull GradleProject gradleEntity,
                        @NotNull Project intellijEntity,
                        @NotNull GradleChangesCalculationContext context)
  {
    calculateProjectChanges(gradleEntity, intellijEntity, context.getCurrentChanges());

    final Set<? extends GradleModule> gradleSubEntities = gradleEntity.getModules();
    final Collection<Module> intellijSubEntities = myPlatformFacade.getModules(intellijEntity);
    GradleDiffUtil.calculate(myModuleChangesCalculator, gradleSubEntities, intellijSubEntities, context);
    
    LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(intellijEntity);
    GradleDiffUtil.calculate(myLibraryChangesCalculator, gradleEntity.getLibraries(), Arrays.asList(libraryTable.getLibraries()), context);
  }

  @NotNull
  @Override
  public Object getIntellijKey(@NotNull Project entity) {
    return entity.getName();
  }

  @NotNull
  @Override
  public Object getGradleKey(@NotNull GradleProject entity, @NotNull GradleChangesCalculationContext context) {
    // TODO den consider the known changes
    return entity.getName();
  }

  private void calculateProjectChanges(@NotNull GradleProject gradleProject,
                                       @NotNull Project intellijProject,
                                       @NotNull Set<GradleProjectStructureChange> currentChanges)
  {
    checkName(gradleProject, intellijProject, currentChanges);
    checkLanguageLevel(gradleProject, intellijProject, currentChanges);
  }

  private static void checkName(@NotNull GradleProject gradleProject,
                                @NotNull Project intellijProject,
                                @NotNull Set<GradleProjectStructureChange> currentChanges)
  {
    String gradleName = gradleProject.getName();
    String intellijName = intellijProject.getName();
    if (!gradleName.equals(intellijName)) {
      currentChanges.add(new GradleProjectRenameChange(gradleName, intellijName));
    }
  }

  private void checkLanguageLevel(@NotNull GradleProject gradleProject,
                                  @NotNull Project intellijProject,
                                  @NotNull Set<GradleProjectStructureChange> currentChanges)
  {
    LanguageLevel gradleLevel = gradleProject.getLanguageLevel();
    LanguageLevel intellijLevel = myPlatformFacade.getLanguageLevel(intellijProject);
    if (gradleLevel != intellijLevel) {
      currentChanges.add(new GradleLanguageLevelChange(gradleLevel, intellijLevel));
    }
  }
}

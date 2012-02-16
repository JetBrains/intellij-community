package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleModule;
import org.jetbrains.plugins.gradle.model.gradle.GradleProject;

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

  private final GradleModuleStructureChangesCalculator myModuleChangesCalculator;
  private final PlatformFacade myStructureHelper;

  public GradleProjectStructureChangesCalculator(@NotNull GradleModuleStructureChangesCalculator moduleCalculator,
                                                 @NotNull PlatformFacade structureHelper) {
    myModuleChangesCalculator = moduleCalculator;
    myStructureHelper = structureHelper;
  }

  @Override
  public void calculate(@NotNull GradleProject gradleEntity,
                        @NotNull Project intellijEntity,
                        @NotNull Set<GradleProjectStructureChange> knownChanges,
                        @NotNull Set<GradleProjectStructureChange> currentChanges)
  {
    calculateProjectChanges(gradleEntity, intellijEntity, currentChanges);

    final Set<? extends GradleModule> gradleSubEntities = gradleEntity.getModules();
    final Collection<Module> intellijSubEntities = myStructureHelper.getModules(intellijEntity);
    GradleDiffUtil.calculate(myModuleChangesCalculator, gradleSubEntities, intellijSubEntities, knownChanges, currentChanges);
  }

  @NotNull
  @Override
  public Object getIntellijKey(@NotNull Project entity) {
    return entity.getName();
  }

  @NotNull
  @Override
  public Object getGradleKey(@NotNull GradleProject entity, @NotNull Set<GradleProjectStructureChange> knownChanges) {
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
    LanguageLevel intellijLevel = myStructureHelper.getLanguageLevel(intellijProject);
    if (gradleLevel != intellijLevel) {
      currentChanges.add(new GradleLanguageLevelChange(gradleLevel, intellijLevel));
    }
  }
}

package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleModule;
import org.jetbrains.plugins.gradle.model.GradleProject;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.jetbrains.plugins.gradle.diff.GradleDiffUtil.concatenate;

/**
 * Encapsulates functionality of calculating changes between Gradle and IntelliJ IDEA project hierarchies.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:55 PM
 */
public class GradleProjectStructureChangesCalculator implements GradleStructureChangesCalculator<GradleProject, Project> {

  private final GradleModuleStructureChangesCalculator myModuleChangesCalculator = new GradleModuleStructureChangesCalculator();

  @NotNull
  @Override
  public Set<GradleProjectStructureChange> calculate(@NotNull GradleProject gradleEntity,
                                                     @NotNull Project intellijEntity,
                                                     @NotNull Set<GradleProjectStructureChange> knownChanges)
  {
    final Set<GradleProjectStructureChange> result = calculateProjectChanges(gradleEntity, intellijEntity, knownChanges);

    final Set<? extends GradleModule> gradleSubEntities = gradleEntity.getModules();
    final List<Module> intellijSubEntities = asList(ModuleManager.getInstance(intellijEntity).getModules());
    return concatenate(result, GradleDiffUtil.calculate(myModuleChangesCalculator, gradleSubEntities, intellijSubEntities, knownChanges));
  }

  @NotNull
  @Override
  public Object getIntellijKey(@NotNull Project entity, @NotNull Set<GradleProjectStructureChange> knownChanges) {
    // TODO den consider the known changes
    return entity.getName();
  }

  @NotNull
  @Override
  public Object getGradleKey(@NotNull GradleProject entity, @NotNull Set<GradleProjectStructureChange> knownChanges) {
    // TODO den consider the known changes
    return entity.getName();
  }

  @NotNull
  private static Set<GradleProjectStructureChange> calculateProjectChanges(@NotNull GradleProject gradleProject,
                                                                           @NotNull Project intellijProject,
                                                                           @NotNull Set<GradleProjectStructureChange> knownChanges)
  {
    final Set<GradleProjectStructureChange> nameChanges = checkName(gradleProject, intellijProject, knownChanges);
    final Set<GradleProjectStructureChange> levelChanges = checkLanguageLevel(gradleProject, intellijProject, knownChanges);
    return concatenate(nameChanges, levelChanges);
  }

  @NotNull
  private static Set<GradleProjectStructureChange> checkName(@NotNull GradleProject gradleProject,
                                                             @NotNull Project intellijProject,
                                                             @NotNull Set<GradleProjectStructureChange> knownChanges)
  {
    String gradleName = gradleProject.getName();
    String intellijName = intellijProject.getName();
    if (gradleName.equals(intellijName)) {
      return Collections.emptySet();
    }
    final GradleRenameChange change = new GradleRenameChange(GradleRenameChange.Entity.PROJECT, gradleName, intellijName);
    return knownChanges.contains(change) ? Collections.<GradleProjectStructureChange>emptySet()
                                         : Collections.<GradleProjectStructureChange>singleton(change);
  }

  @NotNull
  private static Set<GradleProjectStructureChange> checkLanguageLevel(@NotNull GradleProject gradleProject,
                                                                      @NotNull Project intellijProject,
                                                                      @NotNull Set<GradleProjectStructureChange> knownChanges)
  {
    LanguageLevel gradleLevel = gradleProject.getLanguageLevel();
    LanguageLevel intellijLevel = LanguageLevelProjectExtension.getInstance(intellijProject).getLanguageLevel();
    if (gradleLevel == intellijLevel) {
      return Collections.emptySet();
    }
    final GradleLanguageLevelChange change = new GradleLanguageLevelChange(gradleLevel, intellijLevel);
    return knownChanges.contains(change) ? Collections.<GradleProjectStructureChange>emptySet()
                                         : Collections.<GradleProjectStructureChange>singleton(change);
  }
}

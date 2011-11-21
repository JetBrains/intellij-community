package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleModule;
import org.jetbrains.plugins.gradle.model.GradleProject;

import java.util.*;

/**
 * Encapsulates functionality of calculating changes between Gradle and IntelliJ IDEA projects.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:55 PM
 */
public class GradleProjectStructureChangesCalculator {

  private final GradleModuleStructureChangesCalculator myModuleChangesCalculator = new GradleModuleStructureChangesCalculator();

  /**
   * Calculates difference between the given projects.
   *
   * @param gradleProject    target gradle project data holder
   * @param intellijProject  IJ project data holder
   * @param knownChanges     known changes between the given projects
   * @return                 collection that contains differences between the given objects (if any)
   */
  @NotNull
  public Collection<GradleProjectStructureChange> calculateDiff(@NotNull GradleProject gradleProject,
                                                                @NotNull Project intellijProject,
                                                                @NotNull Set<GradleProjectStructureChange> knownChanges)
  {
    Collection<GradleProjectStructureChange> result = calculateProjectChanges(gradleProject, intellijProject, knownChanges);
    final Map<String, Module> intellijModulesByName = new HashMap<String, Module>();
    for (Module module : ModuleManager.getInstance(intellijProject).getModules()) {
      intellijModulesByName.put(module.getName(), module);
    }
    Set<? extends GradleModule> gradleModules = new HashSet<GradleModule>(gradleProject.getModules());
    final Ref<Module> gradleModuleToMap = new Ref<Module>();
    final Ref<Module> intellijModule = new Ref<Module>();
    GradleProjectStructureChangeVisitor visitor = new GradleProjectStructureChangeVisitorAdapter() {
      @Override
      public void visit(@NotNull GradleRenameChange change) {
        if (gradleModuleToMap.get().getName().equals(change.getGradleValue()) && change.getEntity() == GradleRenameChange.Entity.MODULE) {
          intellijModule.set(intellijModulesByName.get(change.getIntellijValue()));
        }
      }
    };
    for (Iterator<? extends GradleModule> i = gradleModules.iterator(); i.hasNext(); ) {
      GradleModule gradleModule = i.next();
      Module module = intellijModulesByName.get(gradleModule.getName());
      if (module == null) {
        for (GradleProjectStructureChange change : knownChanges) {
          change.invite(visitor);
          if ((module = intellijModule.get()) != null) {
            break;
          }
        }
      }
      if (module != null) {
        i.remove();
        intellijModulesByName.remove(module.getName());
        result.addAll(myModuleChangesCalculator.calculateDiff(gradleModule, module, knownChanges));
      }
    }

    for (GradleModule module : gradleModules) {
      result.add(new GradleModulePresenceChange(module, null));
    }
    for (Module module : intellijModulesByName.values()) {
      result.add(new GradleModulePresenceChange(null, module));
    }
    result.removeAll(knownChanges);
    return result;
  }

  @NotNull
  private static Collection<GradleProjectStructureChange> calculateProjectChanges(@NotNull GradleProject gradleProject,
                                                                                  @NotNull Project intellijProject,
                                                                                  @NotNull Set<GradleProjectStructureChange> knownChanges)
  {
    Set<GradleProjectStructureChange> result = new HashSet<GradleProjectStructureChange>();
    checkName(gradleProject, intellijProject, result);
    checkLanguageLevel(gradleProject, intellijProject, result);
    return result;
  }

  private static void checkName(@NotNull GradleProject gradleProject,
                                @NotNull Project intellijProject,
                                @NotNull Collection<GradleProjectStructureChange> result)
  {
    String gradleName = gradleProject.getName();
    String intellijName = intellijProject.getName();
    if (!gradleName.equals(intellijName)) {
      result.add(new GradleRenameChange(GradleRenameChange.Entity.PROJECT, gradleName, intellijName));
    }
  }

  private static void checkLanguageLevel(@NotNull GradleProject gradleProject,
                                         @NotNull Project intellijProject,
                                         @NotNull Collection<GradleProjectStructureChange> result)
  {
    LanguageLevel gradleLevel = gradleProject.getLanguageLevel();
    LanguageLevel intellijLevel = LanguageLevelProjectExtension.getInstance(intellijProject).getLanguageLevel();
    if (gradleLevel != intellijLevel) {
      result.add(new GradleLanguageLevelChange(gradleLevel, intellijLevel));
    }
  }
}

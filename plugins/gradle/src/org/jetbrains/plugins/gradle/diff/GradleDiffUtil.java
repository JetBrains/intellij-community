package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.*;

import java.util.*;

/**
 * Contains various utility methods for building changes between the gradle and intellij project structures.
 * 
 * @author Denis Zhdanov
 * @since 1/24/12 11:20 AM
 */
public class GradleDiffUtil {

  private GradleDiffUtil() {
  }

  /**
   * Build changes objects assuming that all entities starting from the given (i.e. given and all of its interested sub-entities)
   * exist only at the gradle side.
   * <p/>
   * Example: particular module has been added at the gradle side. We want to mark that module, its content root(s), dependencies etc
   * as gradle-local changes.
   * 
   * @param entity          target gradle-local entity
   * @param currentChanges  holder for the changes built during the current call
   */
  public static void buildLocalChanges(@NotNull GradleEntity entity, @NotNull final Set<GradleProjectStructureChange> currentChanges) {
    entity.invite(new GradleEntityVisitor() {
      @Override
      public void visit(@NotNull GradleProject project) {
        assert false;
      }

      @Override
      public void visit(@NotNull GradleModule module) {
        currentChanges.add(new GradleModulePresenceChange(module, null));
        for (GradleDependency dependency : module.getDependencies()) {
          dependency.invite(this);
        }
      }

      @Override
      public void visit(@NotNull GradleContentRoot contentRoot) {
        // TODO den implement 
      }

      @Override
      public void visit(@NotNull GradleLibrary library) {
        // TODO den implement 
      }

      @Override
      public void visit(@NotNull GradleModuleDependency dependency) {
        // TODO den implement 
      }

      @Override
      public void visit(@NotNull GradleLibraryDependency dependency) {
        currentChanges.add(new GradleLibraryDependencyPresenceChange(dependency, null));
      }
    });
  }

  /**
   * Analogues to {@link #buildLocalChanges} but targets intellij entity.
   *
   * @param module          target intellij-local module that doesn't present at the gradle side
   * @param currentChanges  holder for the changes built during the current call
   */
  public static void buildLocalChanges(@NotNull Module module,
                                       @NotNull Set<GradleProjectStructureChange> currentChanges)
  {
    currentChanges.add(new GradleModulePresenceChange(null, module));
    // TODO den process module sub-entities here (content roots and dependencies).
  }

  /**
   * Analogues to {@link #buildLocalChanges} but targets intellij entity.
   * 
   * @param libraryDependency  target intellij-local library dependency that doesn't present at the gradle side
   * @param currentChanges     holder for the changes built during the current call
   */
  public static void buildLocalChanges(@NotNull LibraryOrderEntry libraryDependency,
                                       @NotNull Set<GradleProjectStructureChange> currentChanges)
  {
    final String libraryName = libraryDependency.getLibraryName();
    if (libraryName != null) {
      currentChanges.add(new GradleLibraryDependencyPresenceChange(null, libraryDependency));
    }
  }

  /**
   * Performs argument type-based dispatch and delegates to one of strongly typed <code>'buildLocalChanges()'</code> methods.
   *
   * @param entity          target intellij-local entity that doesn't present at the gradle side
   * @param currentChanges  holder for the changes built during the current call
   */
  public static void buildLocalChanges(@NotNull Object entity, @NotNull Set<GradleProjectStructureChange> currentChanges) {
    if (entity instanceof GradleEntity) {
      buildLocalChanges((GradleEntity)entity, currentChanges);
    }
    else if (entity instanceof Module) {
      buildLocalChanges((Module)entity, currentChanges);
    }
    else if (entity instanceof LibraryOrderEntry) {
      buildLocalChanges((LibraryOrderEntry)entity, currentChanges);
    }
  }

  /**
   * Utility method for comparing entity collections. For example, it may be provided with the collection of gradle modules and
   * collection of intellij modules. Matched entities are found and the comparison is delegated to the given <code>'calculator'</code>.
   * Corresponding changes are generated for the non-matched (local) changes (e.g. particular library dependency presents
   * at the intellij side but not at the gradle).
   * 
   * @param calculator        comparison strategy that works with the single entities (not collection of entities)
   * @param gradleEntities    entities available at the gradle side
   * @param intellijEntities  entities available at the intellij side
   * @param knownChanges      collection that contains known changes about the entities
   * @param currentChanges    holder for the changes discovered during the current call
   * @param <I>               target intellij entity type
   * @param <G>               target gradle entity type
   */
  public static <I, G extends GradleEntity> void calculate(
    @NotNull GradleStructureChangesCalculator<G, I> calculator,
    @NotNull Iterable<? extends G> gradleEntities,
    @NotNull Iterable<? extends I> intellijEntities,
    @NotNull Set<GradleProjectStructureChange> knownChanges,
    @NotNull Set<GradleProjectStructureChange> currentChanges)
  {
    Map<Object, I> intellijEntitiesByKeys = new HashMap<Object, I>();
    for (I entity : intellijEntities) {
      final I previous = intellijEntitiesByKeys.put(calculator.getIntellijKey(entity), entity);
      assert previous == null;
    }
    for (G gradleEntity: gradleEntities) {
      I intellijEntity = intellijEntitiesByKeys.remove(calculator.getGradleKey(gradleEntity, knownChanges));
      if (intellijEntity == null) {
        buildLocalChanges(gradleEntity, currentChanges);
      }
      else {
        calculator.calculate(gradleEntity, intellijEntity, knownChanges, currentChanges);
      }
    }

    for (I entity : intellijEntitiesByKeys.values()) {
      buildLocalChanges(entity, currentChanges);
    }
  }
}

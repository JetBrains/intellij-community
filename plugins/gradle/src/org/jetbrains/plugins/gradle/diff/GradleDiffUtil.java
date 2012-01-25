package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.*;

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
   * @param entity  target gradle-local entity
   * @return        collection of gradle-local changes for the given entity and its interested sub-entities
   */
  public static Set<GradleProjectStructureChange> buildLocalChanges(@NotNull GradleEntity entity) {
    final Set<GradleProjectStructureChange> result = new HashSet<GradleProjectStructureChange>();
    entity.invite(new GradleEntityVisitor() {
      @Override
      public void visit(@NotNull GradleProject project) {
        assert false;
      }

      @Override
      public void visit(@NotNull GradleModule module) {
        result.add(new GradleModulePresenceChange(module, null));
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
        result.add(new GradleLibraryDependencyPresenceChange(dependency, null));
      }
    });
    return result;
  }

  /**
   * Analogues to {@link #buildLocalChanges} but targets intellij entity.
   *
   * @param module  target intellij-local module that doesn't present at the gradle side
   * @return        collection of intellij-local changes for the given entity and its interested sub-entities
   */
  public static Set<? extends GradleProjectStructureChange> buildLocalChanges(@NotNull Module module) {
    Set<GradleProjectStructureChange> result = new HashSet<GradleProjectStructureChange>();
    result.add(new GradleModulePresenceChange(null, module));
    // TODO den process module sub-entities here (content roots and dependencies).
    return result;
  }

  /**
   * Analogues to {@link #buildLocalChanges} but targets intellij entity.
   * 
   * @param libraryDependency  target intellij-local library dependency that doesn't present at the gradle side
   * @return                   collection of intellij-local changes for the given entity and its interested sub-entities
   */
  public static Set<? extends GradleProjectStructureChange> buildLocalChanges(@NotNull LibraryOrderEntry libraryDependency) {
    return Collections.singleton(new GradleLibraryDependencyPresenceChange(null, libraryDependency));
  }

  /**
   * Performs argument type-based dispatch and delegates to one of strongly typed <code>'buildLocalChanges()'</code> methods.
   *
   * @param entity  target intellij-local entity that doesn't present at the gradle side
   * @return        collection of intellij-local changes for the given entity and its interested sub-entities
   */
  @NotNull
  public static Set<? extends GradleProjectStructureChange> buildLocalChanges(@NotNull Object entity) {
    if (entity instanceof GradleEntity) {
      return buildLocalChanges((GradleEntity)entity);
    }
    else if (entity instanceof Module) {
      return buildLocalChanges((Module)entity);
    }
    else if (entity instanceof LibraryOrderEntry) {
      return buildLocalChanges((LibraryOrderEntry)entity);
    }
    else {
      return Collections.emptySet();
    }
  }

  /**
   * Concatenates given entities into the single collection and returns it.
   * <p/>
   * The main idea behind this method is that most of the time we don't expect changes at all, hence, corresponding changes calculators
   * can use {@link Collections#emptySet()}. However, if some sub-nodes do have changes, attempt
   * to {@link Collection#addAll(Collection) merge} them within the empty set mentioned above would cause an exception.
   * <p/>
   * That's why we provide dedicated method for creating new collection as a merge result.
   * 
   * @param collections  collections to merge
   * @return             merge result
   */
  @NotNull
  public static Set<GradleProjectStructureChange> concatenate(Collection<? extends GradleProjectStructureChange>... collections) {
    Set<GradleProjectStructureChange> result = null;
    for (Collection<? extends GradleProjectStructureChange> collection : collections) {
      if (result == null) {
        result = new HashSet<GradleProjectStructureChange>();
      }
      result.addAll(collection);
    }
    return result == null ? Collections.<GradleProjectStructureChange>emptySet() : result;
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
   * @param <I>               target intellij entity type
   * @param <G>               target gradle entity type
   * @return                  set of changes between the given entity collections
   */
  @NotNull
  public static <I, G extends GradleEntity> Set<GradleProjectStructureChange> calculate(
    @NotNull GradleStructureChangesCalculator<G, I> calculator,
    @NotNull Iterable<? extends G> gradleEntities,
    @NotNull Iterable<? extends I> intellijEntities,
    @NotNull Set<GradleProjectStructureChange> knownChanges)
  {
    Set<GradleProjectStructureChange> result = Collections.emptySet();
    Map<Object, I> intellijEntitiesByKeys = new HashMap<Object, I>();
    for (I entity : intellijEntities) {
      final I previous = intellijEntitiesByKeys.put(calculator.getIntellijKey(entity, knownChanges), entity);
      assert previous == null;
    }
    for (G gradleEntity: gradleEntities) {
      I intellijEntity = intellijEntitiesByKeys.remove(calculator.getGradleKey(gradleEntity, knownChanges));
      Set<GradleProjectStructureChange> changesToMerge;
      if (intellijEntity == null) {
        changesToMerge = buildLocalChanges(gradleEntity);
      }
      else {
        changesToMerge = calculator.calculate(gradleEntity, intellijEntity, knownChanges);
      }
      result = concatenate(result, changesToMerge);
    }

    for (I entity : intellijEntitiesByKeys.values()) {
      result = concatenate(result, buildLocalChanges(entity));
    }
    
    return result;
  }
}

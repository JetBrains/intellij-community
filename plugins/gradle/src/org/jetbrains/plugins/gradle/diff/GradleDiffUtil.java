package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.contentroot.GradleContentRootPresenceChange;
import org.jetbrains.plugins.gradle.diff.dependency.GradleLibraryDependencyPresenceChange;
import org.jetbrains.plugins.gradle.diff.dependency.GradleModuleDependencyPresenceChange;
import org.jetbrains.plugins.gradle.diff.library.GradleJarPresenceChange;
import org.jetbrains.plugins.gradle.diff.module.GradleModulePresenceChange;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.gradle.*;
import org.jetbrains.plugins.gradle.model.id.GradleJarId;
import org.jetbrains.plugins.gradle.model.id.GradleLibraryId;
import org.jetbrains.plugins.gradle.model.intellij.IdeEntityVisitor;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.HashMap;
import java.util.Map;

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
   * @param entity   target gradle-local entity
   * @param context  changes calculation context to use
   */
  public static void buildLocalChanges(@NotNull GradleEntity entity, @NotNull final GradleChangesCalculationContext context) {
    entity.invite(new GradleEntityVisitor() {
      @Override
      public void visit(@NotNull GradleProject project) {
        assert false;
      }

      @Override
      public void visit(@NotNull GradleModule module) {
        context.register(new GradleModulePresenceChange(module, null));
        for (GradleContentRoot root : module.getContentRoots()) {
          root.invite(this);
        }
        for (GradleDependency dependency : module.getDependencies()) {
          dependency.invite(this);
        }
      }

      @Override
      public void visit(@NotNull GradleContentRoot contentRoot) {
        context.register(new GradleContentRootPresenceChange(contentRoot, null)); 
      }

      @Override
      public void visit(@NotNull GradleLibrary library) {
        for (LibraryPathType pathType : LibraryPathType.values()) {
          for (String path : library.getPaths(pathType)) {
            GradleJarId jarId = new GradleJarId(path, pathType, new GradleLibraryId(GradleEntityOwner.GRADLE, library.getName()));
            context.register(new GradleJarPresenceChange(jarId, null));
          }
        } 
      }

      @Override
      public void visit(@NotNull GradleJar jar) {
        context.register(new GradleJarPresenceChange(jar.getId(), null));
      }

      @Override
      public void visit(@NotNull GradleModuleDependency dependency) {
        context.register(new GradleModuleDependencyPresenceChange(dependency, null));
      }

      @Override
      public void visit(@NotNull GradleLibraryDependency dependency) {
        context.register(new GradleLibraryDependencyPresenceChange(dependency, null));
      }

      @Override
      public void visit(@NotNull GradleCompositeLibraryDependency dependency) {
        // We expect such composite entities for outdated libraries to appear as 'low-level' project structure changes processing
        // result.
        assert false;
      }
    });
  }

  /**
   * Performs argument type-based dispatch and delegates to one of strongly typed <code>'buildLocalChanges()'</code> methods.
   *
   * @param entity   target ide-local entity that doesn't present at the gradle side
   * @param context  changes calculation context to use
   */
  public static void buildLocalChanges(@NotNull Object entity, @NotNull final GradleChangesCalculationContext context) {
    if (entity instanceof GradleEntity) {
      buildLocalChanges((GradleEntity)entity, context);
    }
    else {
      GradleUtil.dispatch(entity, new IdeEntityVisitor() {
        @Override
        public void visit(@NotNull Project project) {
        }

        @Override
        public void visit(@NotNull Module module) {
          context.register(new GradleModulePresenceChange(null, module));
          for (ModuleAwareContentRoot contentRoot : context.getPlatformFacade().getContentRoots(module)) {
            visit(contentRoot);
          }
          for (OrderEntry entry : context.getPlatformFacade().getOrderEntries(module)) {
            GradleUtil.dispatch(entry, this);
          }
        }

        @Override
        public void visit(@NotNull ModuleAwareContentRoot contentRoot) {
          context.register(new GradleContentRootPresenceChange(null, contentRoot)); 
        }

        @Override
        public void visit(@NotNull LibraryOrderEntry libraryDependency) {
          context.register(new GradleLibraryDependencyPresenceChange(null, libraryDependency));
        }

        @Override
        public void visit(@NotNull ModuleOrderEntry moduleDependency) {
          final Module module = moduleDependency.getModule();
          if (module != null) {
            context.register(new GradleModuleDependencyPresenceChange(null, moduleDependency));
          }
        }

        @Override
        public void visit(@NotNull Library library) {
        }
      });
    }
  }

  /**
   * Utility method for comparing entity collections. For example, it may be provided with the collection of gradle modules and
   * collection of ide modules. Matched entities are found and the comparison is delegated to the given <code>'calculator'</code>.
   * Corresponding changes are generated for the non-matched (local) changes (e.g. particular library dependency presents
   * at the ide side but not at the gradle).
   * 
   * @param calculator        comparison strategy that works with the single entities (not collection of entities)
   * @param gradleEntities    entities available at the gradle side
   * @param ideEntities       entities available at the ide side
   * @param context           changes calculation context
   * @param <I>               target ide entity type
   * @param <G>               target gradle entity type
   */
  public static <I, G extends GradleEntity> void calculate(
    @NotNull GradleStructureChangesCalculator<G, I> calculator,
    @NotNull Iterable<? extends G> gradleEntities,
    @NotNull Iterable<? extends I> ideEntities,
    @NotNull GradleChangesCalculationContext context)
  {
    Map<Object, I> ideEntitiesByKeys = new HashMap<Object, I>();
    for (I entity : ideEntities) {
      final I previous = ideEntitiesByKeys.put(calculator.getIdeKey(entity), entity);
      assert previous == null;
    }
    for (G gradleEntity: gradleEntities) {
      I ideEntity = ideEntitiesByKeys.remove(calculator.getGradleKey(gradleEntity, context));
      if (ideEntity == null) {
        buildLocalChanges(gradleEntity, context);
      }
      else {
        calculator.calculate(gradleEntity, ideEntity, context);
      }
    }

    for (I entity : ideEntitiesByKeys.values()) {
      buildLocalChanges(entity, context);
    }
  }
}

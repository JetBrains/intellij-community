package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.project.change.*;
import com.intellij.openapi.externalSystem.model.project.id.JarId;
import com.intellij.openapi.externalSystem.service.project.change.ExternalProjectChangesCalculationContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.model.project.change.ModulePresenceChange;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.id.LibraryId;
import com.intellij.openapi.externalSystem.util.IdeEntityVisitor;
import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot;
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

  private static final Logger LOG = Logger.getInstance("#" + GradleDiffUtil.class.getName());

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
  public static void buildLocalChanges(@NotNull ProjectEntityData entity, @NotNull final ExternalProjectChangesCalculationContext context) {
    // TODO den implement
//    entity.invite(new ExternalEntityVisitor() {
//      @Override
//      public void visit(@NotNull ProjectData project) {
//        assert false;
//      }
//
//      @Override
//      public void visit(@NotNull ModuleData module) {
//        context.register(new ModulePresenceChange(module, null));
//        for (ContentRootData root : module.getContentRoots()) {
//          root.invite(this);
//        }
//        for (DependencyData dependency : module.getDependencies()) {
//          dependency.invite(this);
//        }
//      }
//
//      @Override
//      public void visit(@NotNull ContentRootData contentRoot) {
//        context.register(new ContentRootPresenceChange(contentRoot, null)); 
//      }
//
//      @Override
//      public void visit(@NotNull LibraryData library) {
//        for (LibraryPathType pathType : LibraryPathType.values()) {
//          for (String path : library.getPaths(pathType)) {
//            JarId jarId = new JarId(path, pathType, new LibraryId(ProjectSystemId.GRADLE, library.getName()));
//            context.register(new JarPresenceChange(jarId, null));
//          }
//        } 
//      }
//
//      @Override
//      public void visit(@NotNull JarData jar) {
//        context.register(new JarPresenceChange(jar.getId(), null));
//      }
//
//      @Override
//      public void visit(@NotNull ModuleDependencyData dependency) {
//        context.register(new ModuleDependencyPresenceChange(dependency, null));
//      }
//
//      @Override
//      public void visit(@NotNull LibraryDependencyData dependency) {
//        context.register(new LibraryDependencyPresenceChange(dependency, null));
//      }
//
//      @Override
//      public void visit(@NotNull CompositeLibraryDependencyData dependency) {
//        // We expect such composite entities for outdated libraries to appear as 'low-level' project structure changes processing
//        // result.
//        assert false;
//      }
//    });
  }

  /**
   * Performs argument type-based dispatch and delegates to one of strongly typed <code>'buildLocalChanges()'</code> methods.
   *
   * @param entity   target ide-local entity that doesn't present at the gradle side
   * @param context  changes calculation context to use
   */
  public static void buildLocalChanges(@NotNull Object entity, @NotNull final ExternalProjectChangesCalculationContext context) {
    if (entity instanceof ProjectEntityData) {
      buildLocalChanges((ProjectEntityData)entity, context);
    }
    else {
      // TODO den implement
//      GradleUtil.dispatch(entity, new IdeEntityVisitor() {
//        @Override
//        public void visit(@NotNull Project project) {
//        }
//
//        @Override
//        public void visit(@NotNull Module module) {
//          context.register(new ModulePresenceChange(null, module));
//          for (ModuleAwareContentRoot contentRoot : context.getPlatformFacade().getContentRoots(module)) {
//            visit(contentRoot);
//          }
//          for (OrderEntry entry : context.getPlatformFacade().getOrderEntries(module)) {
//            GradleUtil.dispatch(entry, this);
//          }
//        }
//
//        @Override
//        public void visit(@NotNull ModuleAwareContentRoot contentRoot) {
//          context.register(new ContentRootPresenceChange(null, contentRoot)); 
//        }
//
//        @Override
//        public void visit(@NotNull LibraryOrderEntry libraryDependency) {
//          context.register(new LibraryDependencyPresenceChange(null, libraryDependency));
//        }
//
//        @Override
//        public void visit(@NotNull ModuleOrderEntry moduleDependency) {
//          final Module module = moduleDependency.getModule();
//          if (module != null) {
//            context.register(new ModuleDependencyPresenceChange(null, moduleDependency));
//          }
//        }
//
//        @Override
//        public void visit(@NotNull Library library) {
//          for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
//            JarId jarId = new JarId(context.getPlatformFacade().getLocalFileSystemPath(file),
//                                                LibraryPathType.BINARY,
//                                                new LibraryId(ProjectSystemId.IDE, GradleUtil.getLibraryName(library)));
//            context.register(new JarPresenceChange(null, jarId));
//          }
//        }
//      });
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
  public static <I, G extends ProjectEntityData> void calculate(
    @NotNull ExternalProjectStructureChangesCalculator<G, I> calculator,
    @NotNull Iterable<? extends G> gradleEntities,
    @NotNull Iterable<? extends I> ideEntities,
    @NotNull ExternalProjectChangesCalculationContext context)
  {
    // TODO den implement
//    Map<Object, I> ideEntitiesByKeys = new HashMap<Object, I>();
//    for (I entity : ideEntities) {
//      Object key = calculator.getIdeKey(entity);
//      final I previous = ideEntitiesByKeys.put(key, entity);
//      assert previous == null : key;
//    }
//    for (G gradleEntity: gradleEntities) {
//      I ideEntity = ideEntitiesByKeys.remove(calculator.getGradleKey(gradleEntity, context));
//      if (ideEntity == null) {
//        buildLocalChanges(gradleEntity, context);
//      }
//      else {
//        calculator.calculate(gradleEntity, ideEntity, context);
//      }
//    }
//
//    for (I entity : ideEntitiesByKeys.values()) {
//      buildLocalChanges(entity, context);
//    }
  }
}

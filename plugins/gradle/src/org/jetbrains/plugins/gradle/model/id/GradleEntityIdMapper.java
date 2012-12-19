package org.jetbrains.plugins.gradle.model.id;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.gradle.*;
import org.jetbrains.plugins.gradle.model.intellij.IntellijEntityVisitor;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;
import org.jetbrains.plugins.gradle.util.GradleUtil;

/**
 * Encapsulates functionality of mapping project structure entities (mutable object) to id object (immutable) and vice versa.
 * That's why we provide {@code 'entity <--> id'} mapping and make it possible to store the unique 'id' element within the node.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/14/12 12:20 PM
 * @see GradleEntityId
 */
public class GradleEntityIdMapper {
  
  @NotNull GradleProjectStructureContext myMappingContext;

  public GradleEntityIdMapper(@NotNull GradleProjectStructureContext context) {
    myMappingContext = context;
  }

  /**
   * Performs {@code 'entity -> id'} mapping. Check class-level javadoc for more details.
   * 
   * @param entity  target entity to map
   * @return        'id object' mapped to the given entity in case of successful match; <code>null</code> otherwise
   * @throws IllegalArgumentException   if it's not possible to map given entity to id
   */
  @SuppressWarnings({"MethodMayBeStatic", "unchecked"})
  @NotNull
  public static <T extends GradleEntityId> T mapEntityToId(@NotNull Object entity) throws IllegalArgumentException {
    final Ref<GradleEntityId> result = new Ref<GradleEntityId>();
    if (entity instanceof GradleEntity) {
      ((GradleEntity)entity).invite(new GradleEntityVisitor() {
        @Override
        public void visit(@NotNull GradleProject project) {
          result.set(new GradleProjectId(GradleEntityOwner.GRADLE));
        }

        @Override
        public void visit(@NotNull GradleModule module) {
          result.set(new GradleModuleId(GradleEntityOwner.GRADLE, module.getName()));
        }

        @Override
        public void visit(@NotNull GradleModuleDependency dependency) {
          result.set(new GradleModuleDependencyId(GradleEntityOwner.GRADLE, dependency.getOwnerModule().getName(), dependency.getName()));
        }

        @Override
        public void visit(@NotNull GradleLibraryDependency dependency) {
          result.set(new GradleLibraryDependencyId(GradleEntityOwner.GRADLE, dependency.getOwnerModule().getName(), dependency.getName()));
        }

        @Override
        public void visit(@NotNull GradleContentRoot contentRoot) {
          result.set(new GradleContentRootId(GradleEntityOwner.GRADLE, contentRoot.getOwnerModule().getName(), contentRoot.getRootPath())); 
        }

        @Override
        public void visit(@NotNull GradleLibrary library) {
          result.set(new GradleLibraryId(GradleEntityOwner.GRADLE, library.getName())); 
        }

        @Override
        public void visit(@NotNull GradleJar jar) {
          result.set(jar.getId());
        }
      });
    }

    if (result.get() == null) {
      GradleUtil.dispatch(entity, new IntellijEntityVisitor() {
        @Override
        public void visit(@NotNull Project project) {
          result.set(new GradleProjectId(GradleEntityOwner.INTELLIJ));
        }

        @Override
        public void visit(@NotNull Module module) {
          result.set(new GradleModuleId(GradleEntityOwner.INTELLIJ, module.getName()));
        }

        @Override
        public void visit(@NotNull ModuleAwareContentRoot contentRoot) {
          final String path = contentRoot.getFile().getPath();
          result.set(new GradleContentRootId(GradleEntityOwner.INTELLIJ, contentRoot.getModule().getName(), path));
        }

        @Override
        public void visit(@NotNull LibraryOrderEntry libraryDependency) {
          String libraryName = libraryDependency.getLibraryName();
          if (libraryName == null) {
            final Library library = libraryDependency.getLibrary();
            if (library != null) {
              libraryName = GradleUtil.getLibraryName(library);
            }
          }
          if (libraryName == null) {
            return;
          }
          result.set(new GradleLibraryDependencyId(GradleEntityOwner.INTELLIJ, libraryDependency.getOwnerModule().getName(), libraryName));
        }

        @Override
        public void visit(@NotNull ModuleOrderEntry moduleDependency) {
          result.set(new GradleModuleDependencyId(
            GradleEntityOwner.INTELLIJ, moduleDependency.getOwnerModule().getName(), moduleDependency.getModuleName()
          ));
        }

        @Override
        public void visit(@NotNull Library library) {
          result.set(new GradleLibraryId(GradleEntityOwner.INTELLIJ, GradleUtil.getLibraryName(library)));
        }
      });
    }
    final Object r = result.get();
    if (r == null) {
      throw new IllegalArgumentException(String.format("Can't map entity '%s' to id element", entity));
    }
    return (T)r;
  }

  /**
   * Performs {@code 'id -> entity'} mapping. Check class-level javadoc for more details.
   * 
   * @param id   target entity id
   * @param <T>  target entity type
   * @return     entity mapped to the given id if any; <code>null</code> otherwise
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public <T> T mapIdToEntity(@NotNull GradleEntityId id) {
    return (T)id.mapToEntity(myMappingContext);
  }
}

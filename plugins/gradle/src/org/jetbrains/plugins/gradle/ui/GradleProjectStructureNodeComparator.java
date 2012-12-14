package org.jetbrains.plugins.gradle.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleSourceOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.GradleEntityType;
import org.jetbrains.plugins.gradle.model.gradle.*;
import org.jetbrains.plugins.gradle.model.id.GradleEntityId;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;
import org.jetbrains.plugins.gradle.model.intellij.IntellijEntityVisitor;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.Comparator;

/**
 * Encapsulates logic of comparing 'sync project structures' tree nodes.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/21/12 2:44 PM
 */
public class GradleProjectStructureNodeComparator implements Comparator<GradleProjectStructureNode<?>> {

  private static final int PROJECT_WEIGHT            = 0;
  private static final int MODULE_WEIGHT             = 1;
  private static final int CONTENT_ROOT_WEIGHT       = 2;
  private static final int SYNTHETIC_WEIGHT          = 3;
  private static final int MODULE_DEPENDENCY_WEIGHT  = 4;
  private static final int LIBRARY_DEPENDENCY_WEIGHT = 5;
  private static final int LIBRARY_WEIGHT            = 6;
  private static final int JAR_WEIGHT                = 7;
  private static final int UNKNOWN_WEIGHT            = 20;

  @NotNull private final GradleProjectStructureContext myContext;

  public GradleProjectStructureNodeComparator(@NotNull GradleProjectStructureContext context) {
    myContext = context;
  }

  @Override
  public int compare(GradleProjectStructureNode<?> n1, GradleProjectStructureNode<?> n2) {
    final GradleProjectStructureNodeDescriptor<? extends GradleEntityId> d1 = n1.getDescriptor();
    final GradleEntityId id1 = d1.getElement();

    final GradleProjectStructureNodeDescriptor<? extends GradleEntityId> d2 = n2.getDescriptor();
    final GradleEntityId id2 = d2.getElement();

    // Put 'gradle-local' nodes at the top.
    if (id1.getOwner() == GradleEntityOwner.GRADLE && id2.getOwner() == GradleEntityOwner.INTELLIJ) {
      return -1;
    }
    else if (id1.getOwner() == GradleEntityOwner.INTELLIJ && id2.getOwner() == GradleEntityOwner.GRADLE) {
      return 1;
    }
    
    // Compare by weight.
    int weight1 = getWeight(id1);
    int weight2 = getWeight(id2);
    if (weight1 != weight2) {
      return weight1 - weight2;
    }
    
    // Compare by name.
    return d1.getName().compareTo(d2.getName());
  }
  
  private int getWeight(@NotNull GradleEntityId id) {
    if (id.getType() == GradleEntityType.SYNTHETIC) {
      return SYNTHETIC_WEIGHT;
    }
    final Object entity = id.mapToEntity(myContext);
    final Ref<Integer> result = new Ref<Integer>();
    if (entity instanceof GradleEntity) {
      ((GradleEntity)entity).invite(new GradleEntityVisitor() {
        @Override
        public void visit(@NotNull GradleProject project) {
          result.set(PROJECT_WEIGHT);
        }

        @Override
        public void visit(@NotNull GradleModule module) {
          result.set(MODULE_WEIGHT);
        }

        @Override
        public void visit(@NotNull GradleContentRoot contentRoot) {
          result.set(CONTENT_ROOT_WEIGHT);
        }

        @Override
        public void visit(@NotNull GradleLibrary library) {
          result.set(LIBRARY_WEIGHT);
        }

        @Override
        public void visit(@NotNull GradleJar jar) {
          result.set(JAR_WEIGHT);
        }

        @Override
        public void visit(@NotNull GradleModuleDependency dependency) {
          result.set(MODULE_DEPENDENCY_WEIGHT);
        }

        @Override
        public void visit(@NotNull GradleLibraryDependency dependency) {
          result.set(LIBRARY_DEPENDENCY_WEIGHT);
        }
      });
    }
    else {
      GradleUtil.dispatch(entity, new IntellijEntityVisitor() {
        @Override
        public void visit(@NotNull Project project) {
          result.set(PROJECT_WEIGHT);
        }

        @Override
        public void visit(@NotNull Module module) {
          result.set(MODULE_WEIGHT);
        }

        @Override
        public void visit(@NotNull ModuleAwareContentRoot contentRoot) {
          int i = 0;
          for (OrderEntry entry : myContext.getPlatformFacade().getOrderEntries(contentRoot.getModule())) {
            if (entry instanceof ModuleSourceOrderEntry) {
              result.set(i);
              return;
            }
            i++;
          }
          result.set(CONTENT_ROOT_WEIGHT);
        }

        @Override
        public void visit(@NotNull LibraryOrderEntry libraryDependency) {
          result.set(getWeight(libraryDependency.getOwnerModule(), libraryDependency));
        }

        @Override
        public void visit(@NotNull ModuleOrderEntry moduleDependency) {
          result.set(getWeight(moduleDependency.getOwnerModule(), moduleDependency));
        }

        @Override
        public void visit(@NotNull Library library) {
          result.set(LIBRARY_WEIGHT);
        }
      });
    }
    final Integer i = result.get();
    return i == null ? UNKNOWN_WEIGHT : i;
  }

  private int getWeight(@NotNull Module module, @NotNull Object entry) {
    int i = 0;
    for (OrderEntry orderEntry : myContext.getPlatformFacade().getOrderEntries(module)) {
      if (orderEntry.equals(entry)) {
        return i;
      }
      i++;
    }
    return UNKNOWN_WEIGHT;
  }
}

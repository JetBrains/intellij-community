package org.jetbrains.plugins.gradle.sync.conflict;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.diff.library.GradleMismatchedLibraryPathChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitorAdapter;
import org.jetbrains.plugins.gradle.model.intellij.IntellijEntityVisitor;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import java.util.Collection;

/**
 * We want to be able to show changes for the conflicting gradle and intellij project structure entities.
 * <p/>
 * For example, there is a possible case that particular library has a different classpath configuration at the gradle and the intellij.
 * We want to be able to show them to the user.
 * <p/>
 * This class allows to retrieve UI controls for such a conflicting changes.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 3/2/12 3:03 PM
 */
public class GradleConflictControlFactory {
  
  @NotNull private final GradleLibraryConflictControlFactory myLibraryFactory;

  public GradleConflictControlFactory(@NotNull GradleLibraryConflictControlFactory factory) {
    myLibraryFactory = factory;
  }

  /**
   * Tries to build UI control for showing the differences between the gradle and intellij setup of the given project structure entity.
   * 
   * @param entity   target entity
   * @param changes  known changes for the given entity
   * @return         UI control for showing the differences between the gradle and intellij setup of the given project structure entity;
   *                 <code>null</code> if there are no differences or if we don't know how to show them
   */
  @Nullable
  public JComponent getDiffControl(@NotNull Object entity, final @NotNull Collection<GradleProjectStructureChange> changes) {
    final Ref<JComponent> result = new Ref<JComponent>();
    GradleUtil.dispatch(entity, new IntellijEntityVisitor() {
      @Override
      public void visit(@NotNull Project project) {
      }

      @Override
      public void visit(@NotNull Module module) {
      }

      @Override
      public void visit(@NotNull ModuleAwareContentRoot contentRoot) {
      }

      @Override
      public void visit(@NotNull LibraryOrderEntry libraryDependency) {
        final Library library = libraryDependency.getLibrary();
        if (library == null) {
          return;
        }
        
        GradleProjectStructureChangeVisitor visitor = new GradleProjectStructureChangeVisitorAdapter() {
          @Override
          public void visit(@NotNull GradleMismatchedLibraryPathChange change) {
            result.set(myLibraryFactory.getControl(library, change));
          }
        };
        for (GradleProjectStructureChange change : changes) {
          if (result.get() != null) {
            return;
          }
          change.invite(visitor);
        }
      }

      @Override
      public void visit(@NotNull ModuleOrderEntry moduleDependency) {
      }
    });
    return result.get();
  }
}

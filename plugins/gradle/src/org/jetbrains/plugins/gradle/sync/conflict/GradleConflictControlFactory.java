package org.jetbrains.plugins.gradle.sync.conflict;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
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

  @NotNull private final GradleProjectConflictControlFactory           myProjectFactory;
  @NotNull private final GradleCommonDependencyConflictControlFactory  myCommonDependencyFactory;

  public GradleConflictControlFactory(@NotNull GradleProjectConflictControlFactory factory,
                                      @NotNull GradleCommonDependencyConflictControlFactory commonDependencyFactory) {
    myProjectFactory = factory;
    myCommonDependencyFactory = commonDependencyFactory;
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
        result.set(myProjectFactory.getControl(changes));
      }

      @Override
      public void visit(@NotNull Module module) {
      }

      @Override
      public void visit(@NotNull ModuleAwareContentRoot contentRoot) {
      }

      @Override
      public void visit(@NotNull LibraryOrderEntry libraryDependency) {
      }

      @Override
      public void visit(@NotNull ModuleOrderEntry moduleDependency) {
        result.set(myCommonDependencyFactory.getControl(moduleDependency, changes));
      }

      @Override
      public void visit(@NotNull Library library) {
      }
    });
    return result.get();
  }
}

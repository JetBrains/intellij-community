package org.jetbrains.plugins.gradle.model.intellij;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 2/14/12 1:51 PM
 */
public class IntellijEntityVisitorAdapter implements IntellijEntityVisitor {
  @Override
  public void visit(@NotNull Project project) {
  }
  @Override
  public void visit(@NotNull Module module) {
  }
  @Override
  public void visit(@NotNull LibraryOrderEntry libraryDependency) {
  }
  @Override
  public void visit(@NotNull ModuleOrderEntry moduleDependency) {
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.actions;

import com.intellij.java.library.JavaLibraryModificationTracker;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Arrays;
import java.util.Set;

import static com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesAndLibrariesScope;
import static org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames.JAVAFX_APPLICATION_APPLICATION;

final class JavaFxTemplateManager {
  static boolean isJavaFxTemplateAvailable(DataContext dataContext, Set<? extends JpsModuleSourceRootType<?>> requiredRootTypes) {
    var project = CommonDataKeys.PROJECT.getData(dataContext);
    var view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (project == null || view == null) {
      return false;
    }

    var directories = view.getDirectories();
    if (directories.length == 0) return false;

    var index = ProjectRootManager.getInstance(project).getFileIndex();
    var underRoot = Arrays.stream(directories)
      .map(PsiDirectory::getVirtualFile)
      .anyMatch(virtualFile -> index.isUnderSourceRootOfType(virtualFile, requiredRootTypes));

    if (!underRoot) return false;

    // root types check is less expensive on start toolbar update than checking module dependencies
    var module = ModuleUtilCore.findModuleForFile(directories[0].getVirtualFile(), project);
    return hasJavaFxDependency(module);
  }

  private static boolean hasJavaFxDependency(@Nullable Module module) {
    if (module == null || module.isDisposed()) return false;

    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, () -> {
      boolean hasClass = JavaPsiFacade.getInstance(module.getProject())
                           .findClass(JAVAFX_APPLICATION_APPLICATION, moduleWithDependenciesAndLibrariesScope(module)) != null;
      return Result.create(hasClass, JavaLibraryModificationTracker.getInstance(module.getProject()));
    });
  }
}

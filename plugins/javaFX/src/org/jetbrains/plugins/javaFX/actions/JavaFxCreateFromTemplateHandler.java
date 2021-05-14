// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.actions;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.JavaCreateFromTemplateHandler;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;

import static com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesAndLibrariesScope;

final class JavaFxCreateFromTemplateHandler extends JavaCreateFromTemplateHandler {
  @Override
  public boolean handlesTemplate(@NotNull FileTemplate template) {
    return "JavaFXApplication".equals(template.getName());
  }

  @Override
  public boolean canCreate(PsiDirectory @NotNull [] dirs) {
    if (dirs.length > 0) {
      Project project = dirs[0].getProject();
      if (DumbService.isDumb(project)) return false;

      Module module = ModuleUtilCore.findModuleForFile(dirs[0].getVirtualFile(), project);
      return module != null && hasJavaFxDependency(module);
    }
    return super.canCreate(dirs);
  }

  private static boolean hasJavaFxDependency(Module module) {
    GlobalSearchScope scope = moduleWithDependenciesAndLibrariesScope(module);
    return JavaPsiFacade.getInstance(module.getProject()).findClass(JavaFxCommonNames.JAVAFX_APPLICATION_APPLICATION, scope) != null;
  }
}

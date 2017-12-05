// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.DescriptionType;
import org.jetbrains.idea.devkit.inspections.InspectionDescriptionInfo;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.Collections;
import java.util.List;

public class DescriptionInspectionGotoRelatedProvider extends GotoRelatedProvider {
  @NotNull
  @Override
  public List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    PsiFile psiFile = context.getData(CommonDataKeys.PSI_FILE);
    if (psiFile == null || psiFile.getFileType() != HtmlFileType.INSTANCE) {
      return Collections.emptyList();
    }

    Module module = context.getData(LangDataKeys.MODULE);
    if (module == null) {
      return Collections.emptyList();
    }
    Project project = module.getProject();
    if (!PsiUtil.isPluginProject(project)) {
      return Collections.emptyList();
    }

    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return Collections.emptyList();
    }
    VirtualFile folder = virtualFile.getParent();

    //TODO support others (intentions, postfix templates)
    if (folder == null || !folder.getName().equals(DescriptionType.INSPECTION.getDescriptionFolder())) {
      return Collections.emptyList();
    }

    PsiClass baseClass = JavaPsiFacade.getInstance(project).findClass(InspectionProfileEntry.class.getCanonicalName(),
                                                                      GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
    if (baseClass == null) {
      return Collections.emptyList();
    }

    Query<PsiClass> query = ClassInheritorsSearch.search(baseClass, module.getModuleWithDependenciesScope(), true, true, false);
    for (PsiClass psiClass : query) {
      InspectionDescriptionInfo info = InspectionDescriptionInfo.create(module, psiClass);
      if (psiFile.equals(info.getDescriptionFile())) {
        return GotoRelatedItem.createItems(Collections.singleton(psiClass));
      }
    }

    return Collections.emptyList();
  }
}

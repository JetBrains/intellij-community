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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.DescriptionCheckerUtil;
import org.jetbrains.idea.devkit.inspections.DescriptionType;
import org.jetbrains.idea.devkit.inspections.InspectionDescriptionInfo;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DescriptionInspectionGotoRelatedProvider extends GotoRelatedProvider {
  @NotNull
  @Override
  public List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    PsiFile descriptionFile = context.getData(CommonDataKeys.PSI_FILE);
    if (descriptionFile == null || descriptionFile.getFileType() != HtmlFileType.INSTANCE) {
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

    VirtualFile virtualFile = descriptionFile.getVirtualFile();
    if (virtualFile == null) {
      return Collections.emptyList();
    }
    VirtualFile folder = virtualFile.getParent();

    //TODO support others (intentions, postfix templates)
    if (folder == null || !folder.getName().equals(DescriptionType.INSPECTION.getDescriptionFolder())) {
      return Collections.emptyList();
    }

    PsiClass baseClass = JavaPsiFacade.getInstance(project).findClass(InspectionProfileEntry.class.getCanonicalName(),
                                                                      GlobalSearchScope.allScope(project));
    if (baseClass == null) {
      return Collections.emptyList();
    }

    // Try to find class by description name first. It may improve performance significantly.
    PsiShortNamesCache psiShortNamesCache = PsiShortNamesCache.getInstance(project);
    String possibleImplementationName = FileUtil.getNameWithoutExtension(descriptionFile.getName()) + "Inspection";
    Set<PsiClass> checkedPossibleImplementation = new HashSet<>();
    for (GlobalSearchScope scope : DescriptionCheckerUtil.searchScopes(module)) {
      PsiClass[] possibleImplementations = psiShortNamesCache.getClassesByName(possibleImplementationName, scope);
      for (PsiClass possibleImplementation : possibleImplementations) {
        if (isTargetInspectionPsiClass(possibleImplementation, descriptionFile, module)) {
          return createGotoRelatedItem(possibleImplementation);
        }
        checkedPossibleImplementation.add(possibleImplementation);
      }
    }

    for (GlobalSearchScope scope : DescriptionCheckerUtil.searchScopes(module)) {
      Query<PsiClass> query = ClassInheritorsSearch.search(baseClass, scope, true, true, false);
      Ref<List<GotoRelatedItem>> resultItems = new Ref<>();
      query.forEach(psiClass -> {
        if (checkedPossibleImplementation.contains(psiClass)) {
          return true; // already tried this class
        }

        if (isTargetInspectionPsiClass(psiClass, descriptionFile, module)) {
          resultItems.set(createGotoRelatedItem(psiClass));
          return false;
        }

        return true;
      });

      if (!resultItems.isNull()) {
        return resultItems.get();
      }
    }

    return Collections.emptyList();
  }

  private static boolean isTargetInspectionPsiClass(PsiClass psiClass, PsiFile descriptionPsiFile, Module module) {
    InspectionDescriptionInfo info = InspectionDescriptionInfo.create(module, psiClass);
    return descriptionPsiFile.equals(info.getDescriptionFile());
  }

  private static List<GotoRelatedItem> createGotoRelatedItem(PsiClass psiClass) {
    return GotoRelatedItem.createItems(Collections.singleton(psiClass));
  }
}

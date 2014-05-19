/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DefaultGutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ConstantFunction;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.DescriptionCheckerUtil;
import org.jetbrains.idea.devkit.inspections.DescriptionType;
import org.jetbrains.idea.devkit.inspections.InspectionDescriptionInfo;
import org.jetbrains.idea.devkit.util.PsiUtil;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DescriptionTypeRelatedItemLineMarkerProvider extends RelatedItemLineMarkerProvider {

  @Override
  protected void collectNavigationMarkers(@NotNull PsiElement element, Collection<? super RelatedItemLineMarkerInfo> result) {
    if (element instanceof PsiClass) {
      process((PsiClass)element, result);
    }
  }

  private static void process(PsiClass psiClass, Collection<? super RelatedItemLineMarkerInfo> result) {
    if (!PsiUtil.isInstantiable(psiClass)) return;

    Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
    if (module == null) return;

    final GlobalSearchScope scope = GlobalSearchScope.moduleRuntimeScope(module, false);
    final PsiClass actionClass = JavaPsiFacade.getInstance(psiClass.getProject())
      .findClass(DescriptionType.INSPECTION.getClassName(), scope);
    if (actionClass == null) return;

    for (DescriptionType type : DescriptionType.values()) {
      if (!InheritanceUtil.isInheritor(psiClass, type.getClassName())) {
        continue;
      }

      String descriptionDirName = DescriptionCheckerUtil.getDescriptionDirName(psiClass);
      if (StringUtil.isEmptyOrSpaces(descriptionDirName)) {
        return;
      }

      if (type == DescriptionType.INSPECTION) {
        final InspectionDescriptionInfo info = InspectionDescriptionInfo.create(module, psiClass);
        if (info.hasDescriptionFile()) {
          addDescriptionFileGutterIcon(psiClass, info.getDescriptionFile(), result);
        }
        return;
      }

      for (PsiDirectory descriptionDir : DescriptionCheckerUtil.getDescriptionsDirs(module, type)) {
        PsiDirectory dir = descriptionDir.findSubdirectory(descriptionDirName);
        if (dir == null) continue;
        final PsiFile descriptionFile = dir.findFile("description.html");
        if (descriptionFile != null) {
          addDescriptionFileGutterIcon(psiClass, descriptionFile, result);

          addBeforeAfterTemplateFilesGutterIcon(psiClass, dir, result);
          return;
        }
      }
      return;
    }
  }

  private static void addDescriptionFileGutterIcon(PsiClass psiClass,
                                                   PsiFile descriptionFile,
                                                   Collection<? super RelatedItemLineMarkerInfo> result) {

    result.add(create(psiClass.getNameIdentifier(), AllIcons.FileTypes.Html,
                      "Description", "", Collections.singleton(descriptionFile)));
  }

  private static void addBeforeAfterTemplateFilesGutterIcon(PsiClass psiClass,
                                                            PsiDirectory descriptionDirectory,
                                                            Collection<? super RelatedItemLineMarkerInfo> result) {
    final List<PsiFile> templateFiles = new SortedList<PsiFile>(new Comparator<PsiFile>() {
      @Override
      public int compare(PsiFile o1, PsiFile o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    for (PsiFile file : descriptionDirectory.getFiles()) {
      final String fileName = file.getName();
      if (fileName.endsWith(".template")) {
        if (fileName.startsWith("after.") ||
            fileName.startsWith("before.")) {
          templateFiles.add(file);
        }
      }
    }
    if (templateFiles.isEmpty()) return;

    result.add(create(psiClass.getNameIdentifier(), AllIcons.Actions.Diff,
                      "Before/After Templates", "Select Template", templateFiles));
  }

  private static RelatedItemLineMarkerInfo<PsiElement> create(PsiElement element,
                                                              Icon icon,
                                                              String tooltip,
                                                              String popupTitle,
                                                              @NotNull Collection<? extends NavigatablePsiElement> targets) {
    return new RelatedItemLineMarkerInfo<PsiElement>(element,
                                                     element.getTextRange(),
                                                     icon,
                                                     Pass.UPDATE_OVERRIDEN_MARKERS,
                                                     new ConstantFunction<PsiElement, String>(tooltip),
                                                     new DefaultGutterIconNavigationHandler<PsiElement>(targets, popupTitle),
                                                     GutterIconRenderer.Alignment.RIGHT,
                                                     GotoRelatedItem.createItems(targets, "DevKit"));
  }
}

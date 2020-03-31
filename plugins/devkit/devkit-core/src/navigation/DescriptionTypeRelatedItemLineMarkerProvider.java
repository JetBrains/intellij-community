/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SortedList;
import icons.DevkitIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.DescriptionCheckerUtil;
import org.jetbrains.idea.devkit.inspections.DescriptionType;
import org.jetbrains.idea.devkit.inspections.InspectionDescriptionInfo;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DescriptionTypeRelatedItemLineMarkerProvider extends DevkitRelatedClassLineMarkerProviderBase {
  private static final NotNullFunction<PsiFile, Collection<? extends PsiElement>> CONVERTER =
    psiFile -> ContainerUtil.createMaybeSingletonList(psiFile);

  private static final NotNullFunction<PsiFile, Collection<? extends GotoRelatedItem>> RELATED_ITEM_PROVIDER =
    psiFile -> GotoRelatedItem.createItems(Collections.singleton(psiFile), "DevKit");

  private final Option myDescriptionOption = new Option("devkit.description", "Description", DevkitIcons.Gutter.DescriptionFile);
  private final Option myBeforeAfterOption = new Option("devkit.beforeAfter", "Before/After templates", DevkitIcons.Gutter.Diff);

  @Override
  public Option @NotNull [] getOptions() {
    return new Option[]{myDescriptionOption, myBeforeAfterOption};
  }

  @Override
  public String getName() {
    return "Description / Before|After templates";
  }

  @Override
  protected void process(@NotNull PsiElement highlightingElement,
                         @NotNull PsiClass psiClass,
                         @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    final boolean descriptionEnabled = myDescriptionOption.isEnabled();
    final boolean beforeAfterEnabled = myBeforeAfterOption.isEnabled();
    if (!descriptionEnabled && !beforeAfterEnabled) return;

    if (!PsiUtil.isInstantiable(psiClass)) return;

    Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
    if (module == null) return;

    for (DescriptionType type : DescriptionType.values()) {
      if (!InheritanceUtil.isInheritor(psiClass, type.getClassName())) {
        continue;
      }

      String descriptionDirName = DescriptionCheckerUtil.getDescriptionDirName(psiClass);
      if (StringUtil.isEmptyOrSpaces(descriptionDirName)) {
        return;
      }

      if (type == DescriptionType.INSPECTION) {
        if (!descriptionEnabled) return;
        final InspectionDescriptionInfo info = InspectionDescriptionInfo.create(module, psiClass);
        if (info.hasDescriptionFile()) {
          addDescriptionFileGutterIcon(highlightingElement, info.getDescriptionFile(), result);
        }
        return;
      }

      for (PsiDirectory descriptionDir : DescriptionCheckerUtil.getDescriptionsDirs(module, type)) {
        PsiDirectory dir = descriptionDir.findSubdirectory(descriptionDirName);
        if (dir == null) continue;
        final PsiFile descriptionFile = dir.findFile("description.html");
        if (descriptionFile != null) {
          if (descriptionEnabled) {
            addDescriptionFileGutterIcon(highlightingElement, descriptionFile, result);
          }

          if (beforeAfterEnabled) {
            addBeforeAfterTemplateFilesGutterIcon(highlightingElement, dir, result);
          }
          return;
        }
      }
      return;
    }
  }

  private static void addDescriptionFileGutterIcon(PsiElement highlightingElement,
                                                   PsiFile descriptionFile,
                                                   Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    final RelatedItemLineMarkerInfo<PsiElement> info = NavigationGutterIconBuilder
      .create(DevkitIcons.Gutter.DescriptionFile, CONVERTER, RELATED_ITEM_PROVIDER)
      .setTarget(descriptionFile)
      .setTooltipText("Description")
      .setAlignment(GutterIconRenderer.Alignment.RIGHT)
      .createLineMarkerInfo(highlightingElement);
    result.add(info);
  }

  private static void addBeforeAfterTemplateFilesGutterIcon(PsiElement highlightingElement,
                                                            PsiDirectory descriptionDirectory,
                                                            Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    final List<PsiFile> templateFiles = new SortedList<>(Comparator.comparing(PsiFileSystemItem::getName));
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

    final RelatedItemLineMarkerInfo<PsiElement> info = NavigationGutterIconBuilder
      .create(DevkitIcons.Gutter.Diff, CONVERTER, RELATED_ITEM_PROVIDER)
      .setTargets(templateFiles)
      .setPopupTitle("Select Template")
      .setTooltipText("Before/After Templates")
      .setAlignment(GutterIconRenderer.Alignment.RIGHT)
      .createLineMarkerInfo(highlightingElement);
    result.add(info);
  }
}

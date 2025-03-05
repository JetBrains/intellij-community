// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.DevKitIcons;
import org.jetbrains.idea.devkit.inspections.DescriptionType;
import org.jetbrains.idea.devkit.inspections.DescriptionTypeResolver;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class DescriptionTypeRelatedItemLineMarkerProvider extends DevkitRelatedClassLineMarkerProviderBase {
  private static final NotNullFunction<PsiFile, Collection<? extends PsiElement>> CONVERTER =
    psiFile -> ContainerUtil.createMaybeSingletonList(psiFile);

  private static final NotNullFunction<PsiFile, Collection<? extends GotoRelatedItem>> RELATED_ITEM_PROVIDER =
    psiFile -> GotoRelatedItem.createItems(Collections.singleton(psiFile), "DevKit");

  private final Option myDescriptionOption = new Option("devkit.description",
                                                        DevKitBundle.message("gutter.related.option.description"),
                                                        DevKitIcons.Gutter.DescriptionFile);
  private final Option myBeforeAfterOption = new Option("devkit.beforeAfter",
                                                        DevKitBundle.message("gutter.related.option.before.after.templates"),
                                                        DevKitIcons.Gutter.Diff);

  @Override
  public Option @NotNull [] getOptions() {
    return new Option[]{myDescriptionOption, myBeforeAfterOption};
  }

  @Override
  public String getName() {
    return DevKitBundle.message("gutter.related.option.name");
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

    for (DescriptionType type : DescriptionType.getEntries()) {
      if (!type.matches(psiClass)) {
        continue;
      }

      DescriptionTypeResolver descriptionTypeResolver = type.createDescriptionTypeResolver(module, psiClass);
      if (descriptionEnabled) {
        PsiFile descriptionFile = descriptionTypeResolver.resolveDescriptionFile();
        if (descriptionFile != null) {
          addDescriptionFileGutterIcon(highlightingElement, descriptionFile, result);
        }
      }

      if (beforeAfterEnabled && type.hasBeforeAfterTemplateFiles()) {
        List<PsiFile> files = descriptionTypeResolver.resolveBeforeAfterTemplateFiles();
        if (!files.isEmpty()) {
          addBeforeAfterTemplateFilesGutterIcon(highlightingElement, files, result);
        }
      }

      return;
    }
  }

  private static void addDescriptionFileGutterIcon(PsiElement highlightingElement,
                                                   PsiFile descriptionFile,
                                                   Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    final RelatedItemLineMarkerInfo<PsiElement> info = NavigationGutterIconBuilder
      .create(DevKitIcons.Gutter.DescriptionFile, CONVERTER, RELATED_ITEM_PROVIDER)
      .setTarget(descriptionFile)
      .setTooltipText(DevKitBundle.message("gutter.related.navigation.popup.description.tooltip"))
      .setAlignment(GutterIconRenderer.Alignment.RIGHT)
      .createLineMarkerInfo(highlightingElement);
    result.add(info);
  }

  private static void addBeforeAfterTemplateFilesGutterIcon(PsiElement highlightingElement,
                                                            List<PsiFile> templateFiles,
                                                            Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    //noinspection DialogTitleCapitalization
    final RelatedItemLineMarkerInfo<PsiElement> info = NavigationGutterIconBuilder
      .create(DevKitIcons.Gutter.Diff, CONVERTER, RELATED_ITEM_PROVIDER)
      .setTargets(templateFiles)
      .setPopupTitle(DevKitBundle.message("gutter.related.navigation.popup.template.title"))
      .setTooltipText(DevKitBundle.message("gutter.related.navigation.popup.template.tooltip"))
      .setAlignment(GutterIconRenderer.Alignment.RIGHT)
      .createLineMarkerInfo(highlightingElement);
    result.add(info);
  }
}

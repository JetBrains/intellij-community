// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UsageTargetUtil {
  private static final ExtensionPointName<UsageTargetProvider> EP_NAME = ExtensionPointName.create("com.intellij.usageTargetProvider");

  /** @deprecated Use {@link #findUsageTargets(Editor, PsiFile, PsiElement)} */
  @Deprecated(forRemoval = true)
  public static UsageTarget[] findUsageTargets(@NotNull DataProvider dataProvider) {
    Editor editor = CommonDataKeys.EDITOR.getData(dataProvider);
    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataProvider);
    PsiElement psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataProvider);
    return findUsageTargets(editor, file, psiElement);
  }

  public static UsageTarget @Nullable [] findUsageTargets(@Nullable Editor editor,
                                                          @Nullable PsiFile file,
                                                          @Nullable PsiElement psiElement) {
    List<UsageTarget> result = new ArrayList<>();
    if (file != null && editor != null) {
      UsageTarget[] targets = findUsageTargets(editor, file);
      Collections.addAll(result, targets);
    }
    if (psiElement != null) {
      UsageTarget[] targets = findUsageTargets(psiElement);
      Collections.addAll(result, targets);
    }
    return result.isEmpty() ? null : result.toArray(UsageTarget.EMPTY_ARRAY);
  }

  public static UsageTarget @NotNull [] findUsageTargets(@NotNull Editor editor, @NotNull PsiFile file) {
    List<UsageTarget> result = new ArrayList<>();
    for (UsageTargetProvider provider : getProviders(file.getProject())) {
      UsageTarget[] targets = provider.getTargets(editor, file);
      if (targets != null) Collections.addAll(result, targets);
    }
    return result.isEmpty() ? UsageTarget.EMPTY_ARRAY : result.toArray(UsageTarget.EMPTY_ARRAY);
  }

  public static UsageTarget @NotNull [] findUsageTargets(@NotNull PsiElement psiElement) {
    List<UsageTarget> result = new ArrayList<>();
    for (UsageTargetProvider provider : getProviders(psiElement.getProject())) {
      UsageTarget[] targets = provider.getTargets(psiElement);
      if (targets != null) Collections.addAll(result, targets);
    }
    return result.isEmpty() ? UsageTarget.EMPTY_ARRAY : result.toArray(UsageTarget.EMPTY_ARRAY);
  }

  @NotNull
  private static List<UsageTargetProvider> getProviders(@NotNull Project project) {
    return DumbService.getDumbAwareExtensions(project, EP_NAME);
  }
}

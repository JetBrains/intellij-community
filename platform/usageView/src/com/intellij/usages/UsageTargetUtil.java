/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.usages;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UsageTargetUtil {
  private static final ExtensionPointName<UsageTargetProvider> EP_NAME = ExtensionPointName.create("com.intellij.usageTargetProvider");

  public static UsageTarget[] findUsageTargets(@NotNull DataProvider dataProvider) {
    Editor editor = CommonDataKeys.EDITOR.getData(dataProvider);
    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataProvider);

    List<UsageTarget> result = new ArrayList<>();
    if (file != null && editor != null) {
      UsageTarget[] targets = findUsageTargets(editor, file);
      if (targets != null) Collections.addAll(result, targets);
    }
    PsiElement psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataProvider);
    if (psiElement != null) {
      UsageTarget[] targets = findUsageTargets(psiElement);
      if (targets != null)Collections.addAll(result, targets);
    }

    return result.isEmpty() ? null : result.toArray(new UsageTarget[result.size()]);
  }

  public static UsageTarget[] findUsageTargets(@NotNull Editor editor, @NotNull PsiFile file) {
    List<UsageTarget> result = new ArrayList<>();
    for (UsageTargetProvider provider : getProviders(file.getProject())) {
      UsageTarget[] targets = provider.getTargets(editor, file);
      if (targets != null) Collections.addAll(result, targets);
    }
    return result.isEmpty() ? null : result.toArray(new UsageTarget[result.size()]);
  }

  public static UsageTarget[] findUsageTargets(@NotNull PsiElement psiElement) {
    List<UsageTarget> result = new ArrayList<>();
    for (UsageTargetProvider provider : getProviders(psiElement.getProject())) {
      UsageTarget[] targets = provider.getTargets(psiElement);
      if (targets != null) Collections.addAll(result, targets);
    }
    return result.isEmpty() ? null : result.toArray(new UsageTarget[result.size()]);
  }

  @NotNull
  private static List<UsageTargetProvider> getProviders(@NotNull Project project) {
    return DumbService.getInstance(project).filterByDumbAwareness(Extensions.getExtensions(EP_NAME));
  }
}

/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import icons.DevkitIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.ExtensionCandidate;
import org.jetbrains.idea.devkit.util.ExtensionLocator;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class ExtensionDeclarationRelatedItemLineMarkerProvider extends DevkitRelatedClassLineMarkerProviderBase {

  @Override
  public String getName() {
    return "Declaration";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return DevkitIcons.Gutter.Plugin;
  }

  @Override
  protected void process(@NotNull PsiElement identifier,
                         @NotNull PsiClass psiClass,
                         @NotNull Collection<? super RelatedItemLineMarkerInfo> result) {
    ExtensionLocator locator = ExtensionLocator.byPsiClass(psiClass);
    List<ExtensionCandidate> targets = locator.findCandidates();
    if (targets.isEmpty()) {
      return;
    }

    RelatedItemLineMarkerInfo<PsiElement> info = LineMarkerInfoHelper.createExtensionLineMarkerInfo(targets, identifier);
    result.add(info);
  }
}

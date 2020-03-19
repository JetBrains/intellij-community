// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import icons.DevkitIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.util.ExtensionCandidate;
import org.jetbrains.idea.devkit.util.ExtensionLocatorKt;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public final class ExtensionDeclarationRelatedItemLineMarkerProvider extends DevkitRelatedClassLineMarkerProviderBase {
  @Override
  public String getName() {
    return "Declaration";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return DevkitIcons.Gutter.Plugin;
  }

  @Override
  protected void process(@NotNull PsiElement identifier,
                         @NotNull PsiClass psiClass,
                         @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    List<ExtensionCandidate> targets = ExtensionLocatorKt.locateExtensionsByPsiClass(psiClass);
    if (targets.isEmpty()) {
      return;
    }

    result.add(LineMarkerInfoHelper.createExtensionLineMarkerInfo(targets, identifier));
  }
}

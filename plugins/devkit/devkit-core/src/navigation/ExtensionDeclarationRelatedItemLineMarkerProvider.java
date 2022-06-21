// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.DevKitIcons;
import org.jetbrains.idea.devkit.util.ExtensionCandidate;
import org.jetbrains.idea.devkit.util.ExtensionLocatorKt;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public final class ExtensionDeclarationRelatedItemLineMarkerProvider extends DevkitRelatedClassLineMarkerProviderBase {

  @Override
  public String getName() {
    return DevKitBundle.message("gutter.related.extension.declaration");
  }

  @Override
  public @NotNull Icon getIcon() {
    return DevKitIcons.Gutter.Plugin;
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

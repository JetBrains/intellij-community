// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementKt;
import org.jetbrains.uast.UastUtils;

import java.util.Collection;

abstract class DevkitRelatedClassLineMarkerProviderBase extends DevkitRelatedLineMarkerProviderBase {
  @Override
  protected final void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo> result) {
    // UAST is used for getting the class identifier to work for all UAST languages (not possible in plain PSI)
    UElement uElement = UastUtils.getUParentForIdentifier(element);
    if (!(uElement instanceof UClass)) {
      return;
    }
    PsiElement identifier = UElementKt.getSourcePsiElement(((UClass)uElement).getUastAnchor());
    if (identifier == null) {
      return;
    }
    process(identifier, ((UClass)uElement).getPsi(), result);
  }

  protected abstract void process(@NotNull PsiElement identifier,
                                  @NotNull PsiClass psiClass,
                                  @NotNull Collection<? super RelatedItemLineMarkerInfo> result);
}

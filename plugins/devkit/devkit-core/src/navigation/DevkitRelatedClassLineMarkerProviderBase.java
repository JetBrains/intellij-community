// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.*;

import java.util.Collection;

abstract class DevkitRelatedClassLineMarkerProviderBase extends DevkitRelatedLineMarkerProviderBase {

  @Override
  protected final void collectNavigationMarkers(@NotNull PsiElement element,
                                                @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    // UAST is used for getting the class identifier to work for all UAST languages (not possible in plain PSI)
    UElement uElement = UastUtils.getUParentForIdentifier(element);
    if (!(uElement instanceof UClass uClass)) {
      return;
    }
    if (uElement instanceof UAnonymousClass) {
      return;
    }

    PsiElement identifier = UElementKt.getSourcePsiElement(uClass.getUastAnchor());
    if (identifier == null) {
      return;
    }

    process(identifier, uClass.getJavaPsi(), result);
  }

  protected abstract void process(@NotNull PsiElement identifier,
                                  @NotNull PsiClass psiClass,
                                  @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result);
}

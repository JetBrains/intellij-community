// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil;

import java.util.Collection;
import java.util.List;

/**
 * Do not process when the current project is not a Plugin project or file is in test sources.
 */
public abstract class DevkitRelatedLineMarkerProviderBase extends RelatedItemLineMarkerProvider {

  @Override
  public final void collectNavigationMarkers(@NotNull List<? extends PsiElement> elements,
                                             @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result,
                                             boolean forNavigation) {
    final PsiElement psiElement = ContainerUtil.getFirstItem(elements);
    if (psiElement == null || !DevKitInspectionUtil.isAllowed(psiElement.getContainingFile())) {
      return;
    }

    super.collectNavigationMarkers(elements, result, forNavigation);
  }

  @Override
  public final boolean isDumbAware() {
    return false;
  }
}

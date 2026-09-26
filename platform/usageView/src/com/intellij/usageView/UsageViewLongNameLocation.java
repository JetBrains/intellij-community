// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usageView;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiQualifiedNamedElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import org.jetbrains.annotations.NotNull;

public final class UsageViewLongNameLocation extends ElementDescriptionLocation {
  private UsageViewLongNameLocation() {
  }

  public static final UsageViewLongNameLocation INSTANCE = new UsageViewLongNameLocation();

  @Override
  public @NotNull ElementDescriptionProvider getDefaultProvider() {
    return DEFAULT_PROVIDER;
  }

  private static final ElementDescriptionProvider DEFAULT_PROVIDER = new ElementDescriptionProvider() {
    @Override
    public String getElementDescription(final @NotNull PsiElement element, final @NotNull ElementDescriptionLocation location) {
      if (location instanceof UsageViewLongNameLocation) {
        if (element instanceof PsiDirectory directory) {
          return SymbolPresentationUtil.getFilePathPresentation(directory);
        }
        if (element instanceof PsiQualifiedNamedElement) {
          return ((PsiQualifiedNamedElement)element).getQualifiedName();
        }
        return UsageViewShortNameLocation.INSTANCE.getDefaultProvider().getElementDescription(
          element, UsageViewShortNameLocation.INSTANCE);
      }
      return null;
    }
  };
}

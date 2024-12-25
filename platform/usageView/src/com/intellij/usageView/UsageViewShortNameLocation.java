// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.usageView;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import org.jetbrains.annotations.NotNull;


public final class UsageViewShortNameLocation extends ElementDescriptionLocation {
  private UsageViewShortNameLocation() {
  }

  public static final UsageViewShortNameLocation INSTANCE = new UsageViewShortNameLocation();

  @Override
  public @NotNull ElementDescriptionProvider getDefaultProvider() {
    return DEFAULT_PROVIDER;
  }

  private static final ElementDescriptionProvider DEFAULT_PROVIDER = new ElementDescriptionProvider() {
    @Override
    public String getElementDescription(final @NotNull PsiElement element, final @NotNull ElementDescriptionLocation location) {
      if (!(location instanceof UsageViewShortNameLocation)) return null;

      if (element instanceof PsiMetaOwner) {
        PsiMetaData metaData = ((PsiMetaOwner)element).getMetaData();
        if (metaData!=null) return DescriptiveNameUtil.getMetaDataName(metaData);
      }

      if (element instanceof PsiNamedElement) {
        return ((PsiNamedElement)element).getName();
      }
      return "";
    }
  };
}

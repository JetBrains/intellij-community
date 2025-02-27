// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class QualifiedNameProviderUtil {
  private QualifiedNameProviderUtil() {}

  public static @Nullable PsiElement adjustElementToCopy(@NotNull PsiElement element) {
    for (QualifiedNameProvider provider : QualifiedNameProvider.EP_NAME.getExtensionList()) {
      PsiElement adjustedElement = provider.adjustElementToCopy(element);
      if (adjustedElement != null) return adjustedElement;
    }
    return null;
  }

  public static @Nullable String getQualifiedName(@NotNull PsiElement element) {
    for (QualifiedNameProvider provider : QualifiedNameProvider.EP_NAME.getExtensionList()) {
      String qualifiedName = provider.getQualifiedName(element);
      if (qualifiedName != null) return qualifiedName;
    }
    return null;
  }

  public static @Nullable PsiElement qualifiedNameToElement(@NotNull String qualifiedName, @NotNull Project project) {
    for (QualifiedNameProvider provider : QualifiedNameProvider.EP_NAME.getExtensionList()) {
      PsiElement element = provider.qualifiedNameToElement(qualifiedName, project);
      if (element != null) return element;
    }
    return null;
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Used to provide appropriate psiElements from usages in Find Usages popup.
 * For instance, it's used in Find Usages popup to help ShowImplementationsAction show
 * psiElement containing a usage
 *
 * @author Konstantin Bulenkov
 */
public abstract class UsageToPsiElementProvider {
  private static final ExtensionPointName<UsageToPsiElementProvider> EP_NAME = ExtensionPointName.create("com.intellij.usageToPsiElementProvider");

  public abstract @Nullable PsiElement getAppropriateParentFrom(PsiElement element);

  public static @Nullable PsiElement findAppropriateParentFrom(@NotNull PsiElement element) {
    return EP_NAME.getExtensionList().stream()
      .map(provider -> provider.getAppropriateParentFrom(element))
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }
}

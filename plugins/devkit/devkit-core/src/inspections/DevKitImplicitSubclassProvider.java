// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.inheritance.ImplicitSubclassProvider;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

final class DevKitImplicitSubclassProvider extends ImplicitSubclassProvider {
  @Override
  public boolean isApplicableTo(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) return false;

    return DevKitImplicitUsageProvider.isDomElementClass(psiClass);
  }

  @Override
  public @Nullable SubclassingInfo getSubclassingInfo(@NotNull PsiClass psiClass) {
    if (DevKitImplicitUsageProvider.isDomElementClass(psiClass)) {
      return new SubclassingInfo(DevKitBundle.message("implemented.at.runtime.dom"));
    }

    return null;
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

final class IntentionDescriptionNotFoundInspection extends DescriptionNotFoundInspectionBase {

  IntentionDescriptionNotFoundInspection() {
    super(DescriptionType.INTENTION);
  }

  @Override
  @NotNull
  protected String getHasNotDescriptionError(@NotNull Module module, @NotNull PsiClass psiClass) {
    return DevKitBundle.message("inspections.intention.description.not.found");
  }

  @Override
  @NotNull
  protected String getHasNotBeforeAfterError() {
    return DevKitBundle.message("inspections.intention.description.no.before.after.template");
  }
}

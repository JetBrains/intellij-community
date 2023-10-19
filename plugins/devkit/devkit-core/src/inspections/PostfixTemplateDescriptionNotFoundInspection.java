// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

final class PostfixTemplateDescriptionNotFoundInspection extends DescriptionNotFoundInspectionBase {

  PostfixTemplateDescriptionNotFoundInspection() {
    super(DescriptionType.POSTFIX_TEMPLATES);
  }

  @Override
  protected boolean skipIfNotRegistered(PsiClass epClass) {
    return false;
  }

  @NotNull
  @Override
  protected String getHasNotDescriptionError(Module module, PsiClass psiClass) {
    return DevKitBundle.message("inspections.postfix.description.not.found");
  }

  @NotNull
  @Override
  protected String getHasNotBeforeAfterError() {
    return DevKitBundle.message("inspections.postfix.description.no.before.after.template");
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

final class PostfixTemplateDescriptionNotFoundInspection extends DescriptionNotFoundInspectionBase {

  PostfixTemplateDescriptionNotFoundInspection() {
    super(DescriptionType.POSTFIX_TEMPLATES);
  }

  @Override
  protected @NotNull String getHasNotDescriptionError(@NotNull DescriptionTypeResolver descriptionTypeResolver) {
    return DevKitBundle.message("inspections.postfix.description.not.found");
  }

  @Override
  protected @NotNull String getHasNotBeforeAfterError() {
    return DevKitBundle.message("inspections.postfix.description.no.before.after.template");
  }
}

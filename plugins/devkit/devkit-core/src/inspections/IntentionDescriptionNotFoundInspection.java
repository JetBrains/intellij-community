// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

@ApiStatus.Internal
public final class IntentionDescriptionNotFoundInspection extends DescriptionNotFoundInspectionBase {

  IntentionDescriptionNotFoundInspection() {
    super(DescriptionType.INTENTION);
  }

  @Override
  protected @NotNull String getHasNotDescriptionError(@NotNull DescriptionTypeResolver descriptionTypeResolver) {
    return DevKitBundle.message("inspections.intention.description.not.found");
  }

  @Override
  protected @NotNull String getHasNotBeforeAfterError() {
    return DevKitBundle.message("inspections.intention.description.no.before.after.template");
  }
}

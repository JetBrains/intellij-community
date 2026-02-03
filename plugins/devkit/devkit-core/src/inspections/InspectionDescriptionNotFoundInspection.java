// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.idea.devkit.DevKitBundle;

@VisibleForTesting
@ApiStatus.Internal
public final class InspectionDescriptionNotFoundInspection extends DescriptionNotFoundInspectionBase {

  InspectionDescriptionNotFoundInspection() {
    super(DescriptionType.INSPECTION);
  }

  @Override
  protected @InspectionMessage @NotNull String getHasNotDescriptionError(@NotNull DescriptionTypeResolver descriptionTypeResolver) {
    final PsiMethod shortNameMethod = descriptionTypeResolver.getUserData(DescriptionTypeResolverKeys.INSPECTION_SHORT_NAME_METHOD);
    final String methodName = shortNameMethod == null ? "" : " [" + shortNameMethod.getName() + "()]";
    return DevKitBundle.message("inspections.inspection.description.optional.short.name", methodName);
  }

  @Override
  protected @InspectionMessage @NotNull String getHasNotBeforeAfterError() {
    throw new IllegalStateException();
  }
}

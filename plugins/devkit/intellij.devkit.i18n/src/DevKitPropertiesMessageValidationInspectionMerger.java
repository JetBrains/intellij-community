// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.i18n;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NotNull;

final class DevKitPropertiesMessageValidationInspectionMerger extends InspectionElementsMerger {

  @NotNull
  @Override
  public String getMergedToolName() {
    return "DevKitPropertiesMessageValidation";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {
      "DevKitPropertiesQuotesValidation"
    };
  }
}

// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NotNull;

public class RedundantStringOperationMerger extends InspectionElementsMerger {
  @NotNull
  @Override
  public String getMergedToolName() {
    return "RedundantStringOperation";
  }

  @NotNull
  @Override
  public String[] getSourceToolNames() {
    return new String[] {
      "StringToString", "RedundantStringToString",
      "SubstringZero", "ConstantStringIntern"
    };
  }
}

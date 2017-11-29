// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.codeInspection.booleanIsAlwaysInverted;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.InvertBooleanFix;
import org.jetbrains.annotations.NotNull;

public class BooleanMethodIsAlwaysInvertedInspection extends BooleanMethodIsAlwaysInvertedInspectionBase {
  @NotNull
  @Override
  protected InvertBooleanFix getInvertBooleanFix() {
    return new InvertBooleanFix(InspectionGadgetsBundle.message("invert.method.quickfix"));
  }
}
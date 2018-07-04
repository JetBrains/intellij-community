// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.booleanIsAlwaysInverted.BooleanMethodIsAlwaysInvertedInspection;

public class BooleanMethodIsAlwaysInvertedLocalInspectionTest extends BooleanMethodIsAlwaysInvertedInspectionTest {

  protected void doTest(boolean checkRange) {
    doTest("invertedBoolean/" + getTestName(true), new BooleanMethodIsAlwaysInvertedInspection().getSharedLocalInspectionTool());
  }
}

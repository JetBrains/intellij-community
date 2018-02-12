// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class RawUseOfParameterizedTypeInspectionTest extends LightInspectionTestCase {

  public void testRawUseOfParameterizedType() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final RawUseOfParameterizedTypeInspection inspection = new RawUseOfParameterizedTypeInspection();
    inspection.ignoreObjectConstruction = false;
    inspection.ignoreUncompilable = true;
    inspection.ignoreParametersOfOverridingMethods = true;
    inspection.ignoreTypeCasts = true;
    return inspection;
  }
}
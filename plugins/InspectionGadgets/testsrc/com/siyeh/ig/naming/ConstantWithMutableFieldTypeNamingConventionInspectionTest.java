// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConstantWithMutableFieldTypeNamingConventionInspectionTest extends AbstractFieldNamingConventionInspectionTest {

  public void testConstantWithMutableFieldTypeNamingConvention() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    FieldNamingConventionInspection inspection = new FieldNamingConventionInspection();
    inspection.setEnabled(true, new ConstantWithMutableFieldTypeNamingConvention().getShortName());
    return inspection;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8_ANNOTATED;
  }
}
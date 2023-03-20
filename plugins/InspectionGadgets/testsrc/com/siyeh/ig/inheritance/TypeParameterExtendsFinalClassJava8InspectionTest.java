// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class TypeParameterExtendsFinalClassJava8InspectionTest extends LightJavaInspectionTestCase {

  public void testTypeParameterExtendsFinalClassJava8() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new TypeParameterExtendsFinalClassInspection();
  }

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}

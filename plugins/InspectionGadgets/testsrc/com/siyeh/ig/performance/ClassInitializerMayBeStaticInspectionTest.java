// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ClassInitializerMayBeStaticInspectionTest extends LightJavaInspectionTestCase {

  public void testEmptyInitializer() {
    doTest();
  }

  public void testAnonymousClass() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ClassInitializerMayBeStaticInspection();
  }
}
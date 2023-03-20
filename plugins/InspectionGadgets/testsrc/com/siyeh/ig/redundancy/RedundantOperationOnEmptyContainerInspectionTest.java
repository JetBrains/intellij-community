// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RedundantOperationOnEmptyContainerInspectionTest extends LightJavaInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8_ANNOTATED;
  }

  public void testIterationOverEmptyContainer() {
    doTest();
  }
  public void testMethodCallsOnEmptyContainer() {
    doTest();
  }
  public void testStaticInitializer() {
    doTest();
  }
  public void testEmptyCollectionReturnThis() { 
    doTest();
  }
  public void testTernaryInQualifier() { 
    doTest();
  }
  public void testAnonymousClass() { 
    doTest();
  }
  public void testSpringCollectionUtils() { 
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new RedundantOperationOnEmptyContainerInspection();
  }
}
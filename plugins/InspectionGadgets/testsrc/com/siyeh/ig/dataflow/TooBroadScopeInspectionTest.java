// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class TooBroadScopeInspectionTest extends LightJavaInspectionTestCase {

  public void testTooBroadScope() {
    doTest();
    checkQuickFixAll();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new TooBroadScopeInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/dataflow/scope";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
  }
}
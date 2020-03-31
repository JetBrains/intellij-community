// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class ForCanBeForeachInspectionTest extends LightJavaInspectionTestCase {

  public void testForCanBeForEach() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ForCanBeForeachInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/migration/foreach";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
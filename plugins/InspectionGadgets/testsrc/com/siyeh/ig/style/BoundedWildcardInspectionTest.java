// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BoundedWildcardInspectionTest extends LightInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/style/bounded_wildcard";
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package my;" +
      "public interface Processor<T> {" +
      "    boolean process(T t);" +
      "}",
    };
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    BoundedWildcardInspection inspection = new BoundedWildcardInspection();
    if (getTestName(false).contains("InvariantSwitchedOff")) {
      inspection.REPORT_INVARIANT_CLASSES = false;
    }
    if (getTestName(false).contains("PrivateMethodsSwitchedOff")) {
      inspection.REPORT_PRIVATE_METHODS = false;
    }
    return inspection;
  }

  public void testSimple() {
    doTest();
    assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR));
  }

  public void testInvariantSwitchedOff() {
    doTest();
    assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR));
  }

  public void testPrivateMethodsSwitchedOff() {
    doTest();
    assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR));
  }
}
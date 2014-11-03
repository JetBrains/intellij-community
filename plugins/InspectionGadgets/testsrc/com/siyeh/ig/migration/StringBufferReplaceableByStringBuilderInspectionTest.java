package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class StringBufferReplaceableByStringBuilderInspectionTest extends LightInspectionTestCase {

  public void testStringBufferReplaceableByStringBuilder() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new StringBufferReplaceableByStringBuilderInspection();
  }
}
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class StringBufferReplaceableByStringBuilderInspectionTest extends LightJavaInspectionTestCase {

  public void testStringBufferReplaceableByStringBuilder() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new StringBufferReplaceableByStringBuilderInspection();
  }
}
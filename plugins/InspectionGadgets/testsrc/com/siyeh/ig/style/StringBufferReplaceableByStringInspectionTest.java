package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class StringBufferReplaceableByStringInspectionTest extends LightInspectionTestCase {

  public void testStringBufferReplaceableByString() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new StringBufferReplaceableByStringInspection();
  }
}
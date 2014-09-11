package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class AmbiguousMethodCallInspectionTest extends LightInspectionTestCase {

  public void testAmbiguousMethodCall() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AmbiguousMethodCallInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/visibility/ambiguous";
  }
}
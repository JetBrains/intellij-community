package com.siyeh.ig.security;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class DesignForExtensionInspectionTest extends LightInspectionTestCase {

  public void testDesignForExtension() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new DesignForExtensionInspection();
  }
}
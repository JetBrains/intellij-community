package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class MalformedFormatStringInspectionTest extends LightInspectionTestCase {

  public void testMalformedFormatString() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final MalformedFormatStringInspection inspection = new MalformedFormatStringInspection();
    inspection.classNames.add("com.siyeh.igtest.bugs.malformed_format_string.MalformedFormatString.SomeOtherLogger");
    inspection.methodNames.add("d");
    return inspection;
  }
}
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class FieldMayBeFinalInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }

  public void testFieldMayBeFinal() {
    doTest();
  }
  public void testAnonymousObject() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new FieldMayBeFinalInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/style/field_final";
  }
}
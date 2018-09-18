package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class ForCanBeForeachInspectionTest extends LightInspectionTestCase {

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
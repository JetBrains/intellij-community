package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryToStringCallInspectionTest extends LightJavaInspectionTestCase {

  public void testUnnecessaryToString() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryToStringCallInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/style/unnecessary_tostring";
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package org.slf4j; public interface Logger { void info(String format, Object... arguments); }",
      "package org.slf4j; public class LoggerFactory { public static Logger getLogger(Class clazz) { return null; }}"};
  }
}
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryCallToStringValueOfInspectionTest extends LightJavaInspectionTestCase {

  public void testUnnecessaryCallToStringValueOf() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryCallToStringValueOfInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/style/unnecessary_valueof";
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package org.slf4j; public interface Logger { void info(String format, Object... arguments); }",
      "package org.slf4j; public class LoggerFactory { public static Logger getLogger(Class clazz) { return null; }}"};
  }
}
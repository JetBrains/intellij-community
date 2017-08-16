package com.intellij.codeInspection.i18n;

import com.intellij.lang.properties.UnusedMessageFormatParameterInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.InspectionTestCase;

public class UnusedMessageFormatParameterInspectionTest extends InspectionTestCase {
  private void doTest() {
    doTest("unusedParameter/" + getTestName(true), new UnusedMessageFormatParameterInspection());
  }

  public void testSimpleString() {
    doTest();
  }

  public void testCorruptedValue() {
    doTest();
  }

  public void testWithChoiceFormat() {
    doTest();
  }

  public void testRegexp() {
    doTest();
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("java-i18n") + "/testData/inspections";
  }
}

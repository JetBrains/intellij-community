package com.intellij.codeInspection.i18n;

import com.intellij.lang.properties.UnusedMessageFormatParameterInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.InspectionTestCase;

/**
 * User: anna
 * Date: 09-Sep-2005
 */
public class UnusedMessageFormatParameterInspectionTest extends InspectionTestCase {
  private void doTest() throws Exception {
    doTest("unusedParameter/" + getTestName(true), new UnusedMessageFormatParameterInspection());
  }

  public void testSimpleString() throws Exception {
    doTest();
  }

  public void testCorruptedValue() throws Exception{
    doTest();
  }

  public void testWithChoiceFormat() throws Exception{
    doTest();
  }

  public void testRegexp() throws Exception {
    doTest();
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("java-i18n") + "/testData/inspections";
  }
}

package com.intellij.codeInspection.i18n;

import com.intellij.lang.properties.UnusedMessageFormatParameterInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class UnusedMessageFormatParameterInspectionTest extends LightCodeInsightFixtureTestCase {

  private void doTest() {
    myFixture.enableInspections(new UnusedMessageFormatParameterInspection());
    myFixture.testHighlighting(getTestName(false) + ".properties");
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
    return PluginPathManager.getPluginHomePath("java-i18n") + "/testData/inspections/unusedParameter";
  }
}

package com.intellij.codeInspection.i18n;

import com.intellij.lang.properties.UnusedMessageFormatParameterInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class UnusedMessageFormatParameterInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(PluginPathManager.getPluginHomePath("java-i18n") + "/testData/inspections/unusedParameter");
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new UnusedMessageFormatParameterInspection());
    myFixture.testHighlighting(myFixture.getTestName(false) + ".properties");
  }
  @Test
  public void testSimpleString() {
    doTest();
  }

  @Test
  public void testCorruptedValue() {
    doTest();
  }

  @Test
  public void testWithChoiceFormat() {
    doTest();
  }

  @Test
  public void testRegexp() {
    doTest();
  }
}

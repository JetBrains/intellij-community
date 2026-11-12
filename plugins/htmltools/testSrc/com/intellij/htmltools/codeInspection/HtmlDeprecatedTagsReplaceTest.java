package com.intellij.htmltools.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlDeprecatedTagInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlPresentationalElementInspection;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.util.XmlUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.List;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/plugins/htmltools/testData/inspections")
public class HtmlDeprecatedTagsReplaceTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String myTestMethodName;

  static final String BASE_PATH = "/deprecatedTag";

  private String myOldDoctype;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myTestMethodName = testInfo.getTestMethod().orElseThrow().getName();
    final ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    myOldDoctype = manager.getDefaultHtmlDoctype(myFixture.getProject());
    manager.setDefaultHtmlDoctype(XmlUtil.XHTML_URI, myFixture.getProject());
    myFixture.enableInspections(new HtmlDeprecatedTagInspection(), new HtmlPresentationalElementInspection());
  }

  @AfterEach
  void tearDown() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExternalResourceManagerEx.getInstanceEx().setDefaultHtmlDoctype(myOldDoctype, myFixture.getProject());
  }

  @Test
  public void testXml() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + "/html_deptag5.xml");
    final List<IntentionAction> list = myFixture.getAvailableIntentions();
    final IntentionAction action = CodeInsightTestUtil.findIntentionByText(list, "Replace font tag with CSS");
    assertNull(action);
  }

  @Test
  public void testFitMenu() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + "/html_deptag5.xhtml");
    final List<IntentionAction> list = myFixture.getAvailableIntentions();
    final IntentionAction action = CodeInsightTestUtil.findIntentionByText(list, "Replace menu tag with ul tag");
    assertNull(action);
  }

  @Test
  public void testSwitchToHtml5QuickFix() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + '/' + getTestName(false) + ".html");
    final List<IntentionAction> list = myFixture.getAvailableIntentions();
    final IntentionAction action = CodeInsightTestUtil.findIntentionByText(list, XmlAnalysisBundle.message("html.quickfix.switch.to.html5"));
    assertNotNull(action);
  }

  @Test
  public void testSwitchToHtml5QuickFix1() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + '/' + getTestName(false) + ".html");
    final List<IntentionAction> list = myFixture.getAvailableIntentions();
    final IntentionAction action = CodeInsightTestUtil.findIntentionByText(list, XmlAnalysisBundle.message("html.quickfix.switch.to.html5"));
    assertNull(action);
  }

  private String getTestName(boolean lowercaseFirstLetter) {
    return PlatformTestUtil.getTestName(myTestMethodName, lowercaseFirstLetter);
  }
}

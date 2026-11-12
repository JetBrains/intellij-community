package com.intellij.htmltools.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitorBasedInspection;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xml.util.XmlUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;

/**
 * @author Maxim.Mossienko
 */
@TestApplication
@TestDataPath("$PROJECT_ROOT/community/plugins/htmltools/testData/inspections")
public class GlobalHtmlHighlightingTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String myTestMethodName;

  private String myOldDoctype;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myTestMethodName = testInfo.getTestMethod().orElseThrow().getName();
    final ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    myOldDoctype = manager.getDefaultHtmlDoctype(myFixture.getProject());
    manager.setDefaultHtmlDoctype(XmlUtil.XHTML_URI, myFixture.getProject());
    myFixture.enableInspections(new XmlHighlightVisitorBasedInspection());
  }

  @AfterEach
  void tearDown() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExternalResourceManagerEx.getInstanceEx().setDefaultHtmlDoctype(myOldDoctype, myFixture.getProject());
  }

  @Test
  public void testReportingXmlHighlightVisitorProblems() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(getBasePath() + getTestName(false) + ".xhtml");
    myFixture.testHighlighting();
  }

  private static String getBasePath() {
    return "xmlHighlightVisitor/";
  }

  private String getTestName(boolean lowercaseFirstLetter) {
    return PlatformTestUtil.getTestName(myTestMethodName, lowercaseFirstLetter);
  }
}
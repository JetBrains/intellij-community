package com.intellij.htmltools.codeInsight.daemon;

import com.intellij.codeInspection.htmlInspections.HtmlExtraClosingTagInspection;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownAttributeInspection;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownTagInspection;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.codeInspection.htmlInspections.XmlWrongRootElementInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlDeprecatedTagInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlPresentationalElementInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.xml.util.CheckXmlFileWithXercesValidatorInspection;
import com.intellij.xml.util.XmlDuplicatedIdInspection;
import com.intellij.xml.util.XmlInvalidIdInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;

@PerformanceUnitTest
@StressTestApplication
@TestDataPath("$PROJECT_ROOT/community/plugins/htmltools/testData/highlighting")
public class HtmlHighlightingPerformanceTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String myTestMethodName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myTestMethodName = testInfo.getTestMethod().orElseThrow().getName();
    myFixture.enableInspections(
      new RequiredAttributesInspection(),
      new HtmlExtraClosingTagInspection(),
      new HtmlUnknownAttributeInspection(),
      new HtmlUnknownTagInspection(),
      new XmlWrongRootElementInspection(),
      new XmlDuplicatedIdInspection(),
      new XmlInvalidIdInspection(),
      new CheckXmlFileWithXercesValidatorInspection(),
      new HtmlDeprecatedTagInspection(),
      new HtmlPresentationalElementInspection()
    );
  }

  @Test
  public void testPerformance2() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles(getTestName(false) + ".html", "manual.css");
    Benchmark.newBenchmark("HTML Highlighting 2", () -> doTest())
      .warmupIterations(1)
      .start();
  }

  @Test
  public void testPerformance() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles(getTestName(false) + ".html", "stylesheet.css");
    Benchmark.newBenchmark("HTML Highlighting", () -> doTest())
      .warmupIterations(1)
      .start();
  }

  protected void doTest() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.doHighlighting(HighlightSeverity.WARNING);
  }

  private String getTestName(boolean lowercaseFirstLetter) {
    return PlatformTestUtil.getTestName(myTestMethodName, lowercaseFirstLetter);
  }
}

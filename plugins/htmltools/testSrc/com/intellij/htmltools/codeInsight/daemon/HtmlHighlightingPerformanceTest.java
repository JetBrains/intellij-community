package com.intellij.htmltools.codeInsight.daemon;

import com.intellij.codeInspection.htmlInspections.*;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlDeprecatedTagInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlPresentationalElementInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.PathManager;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.xml.util.CheckXmlFileWithXercesValidatorInspection;
import com.intellij.xml.util.XmlDuplicatedIdInspection;
import com.intellij.xml.util.XmlInvalidIdInspection;


public class HtmlHighlightingPerformanceTest extends BasePlatformTestCase {
  @Override
  public String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/htmltools/testData/highlighting";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
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

  public void testPerformance2() {
    myFixture.configureByFiles(getTestName(false) + ".html", "manual.css");
    Benchmark.newBenchmark("HTML Highlighting 2", () -> doTest())
      .warmupIterations(1)
      .start();
  }

  public void testPerformance() {
    myFixture.configureByFiles(getTestName(false) + ".html", "stylesheet.css");
    Benchmark.newBenchmark("HTML Highlighting", () -> doTest())
      .warmupIterations(1)
      .start();
  }

  protected void doTest() {
    myFixture.doHighlighting(HighlightSeverity.WARNING);
  }
}

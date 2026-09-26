package com.intellij.htmltools.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlDeprecatedTagInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlPresentationalElementInspection;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HtmlDeprecatedTagsReplaceTest extends BasePlatformTestCase {
  static final String BASE_PATH = "/deprecatedTag";

  private String myOldDoctype;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    myOldDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype(XmlUtil.XHTML_URI, getProject());
    myFixture.enableInspections(new HtmlDeprecatedTagInspection(), new HtmlPresentationalElementInspection());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      ExternalResourceManagerEx.getInstanceEx().setDefaultHtmlDoctype(myOldDoctype, getProject());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected String getBasePath() {
    return BASE_PATH + "/actions";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/htmltools/testData/inspections";
  }

  public void testXml() {
    myFixture.configureByFile(BASE_PATH + "/html_deptag5.xml");
    final List<IntentionAction> list = myFixture.getAvailableIntentions();
    final IntentionAction action = CodeInsightTestUtil.findIntentionByText(list, "Replace font tag with CSS");
    assertNull(action);
  }

  public void testFitMenu() {
    myFixture.configureByFile(BASE_PATH + "/html_deptag5.xhtml");
    final List<IntentionAction> list = myFixture.getAvailableIntentions();
    final IntentionAction action = CodeInsightTestUtil.findIntentionByText(list, "Replace menu tag with ul tag");
    assertNull(action);
  }

  public void testSwitchToHtml5QuickFix() {
    myFixture.configureByFile(BASE_PATH + '/' + getTestName(false) + ".html");
    final List<IntentionAction> list = myFixture.getAvailableIntentions();
    final IntentionAction action = CodeInsightTestUtil.findIntentionByText(list, XmlAnalysisBundle.message("html.quickfix.switch.to.html5"));
    assertNotNull(action);
  }

  public void testSwitchToHtml5QuickFix1() {
    myFixture.configureByFile(BASE_PATH + '/' + getTestName(false) + ".html");
    final List<IntentionAction> list = myFixture.getAvailableIntentions();
    final IntentionAction action = CodeInsightTestUtil.findIntentionByText(list, XmlAnalysisBundle.message("html.quickfix.switch.to.html5"));
    assertNull(action);
  }
}

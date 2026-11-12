package com.intellij.htmltools.codeInspection;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlRequiredAltAttributeInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlRequiredLangAttributeInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlRequiredSummaryAttributeInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlRequiredTitleElementInspection;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xml.psi.XmlPsiBundle;
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
public class HtmlInsertRequiredAttributeTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String myTestMethodName;

  public static final String BASE_PATH = "/insertRequiredAttribute";

  @BeforeEach
  void setUp(TestInfo testInfo) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myTestMethodName = testInfo.getTestMethod().orElseThrow().getName();
    myFixture.enableInspections(new RequiredAttributesInspection(),
                                new HtmlRequiredAltAttributeInspection(),
                                new HtmlRequiredLangAttributeInspection(),
                                new HtmlRequiredTitleElementInspection(),
                                new HtmlRequiredSummaryAttributeInspection());
  }

  @Test
  public void testInsertDefaultAttribute() {
    doSingleTest();
  }

  @Test
  public void testDefaultWithChangedQuote() {
    final HtmlCodeStyleSettings settings = getHtmlCodeStyleSettings();
    final CodeStyleSettings.QuoteStyle oldQuote = settings.HTML_QUOTE_STYLE;
    try {
      settings.HTML_QUOTE_STYLE = CodeStyleSettings.QuoteStyle.Single;
      doSingleTest();
    }
    finally {
      settings.HTML_QUOTE_STYLE = oldQuote;
    }
  }

  private void doSingleTest() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + "/before" + getTestName(false) + ".html");
    var intention = myFixture.findSingleIntention("Insert required attribute 'src'");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(BASE_PATH + "/after" + getTestName(false) + ".html");
  }

  @Test
  public void testInsertRequiredAltInput() {
    checkInspection("insertRequiredAltInput_before.html", "insertRequiredAltInput_after.html", "alt",
                    XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "alt"));
  }

  @Test
  public void testInsertRequiredAltArea() {
    checkInspection("insertRequiredAltArea_before.html", "insertRequiredAltArea_after.html", "alt",
                    XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "alt"));
  }

  @Test
  public void testInsertRequiredAltImg() {
    checkInspection("insertRequiredAltImg_before.html", "insertRequiredAltImg_after.html", "alt",
                    XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "alt"));
  }
  @Test
  public void testInsertRequiredAltImg_noWarning() {
    checkInspectionDoesNotExist("insertRequiredAltImg_after.html",
                                XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "alt"), "alt");
  }

  @Test
  public void testInsertRequiredAltImgVue_noWarning() {
    checkInspectionDoesNotExist("insertRequiredAltImg_after.vue",
                                XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "alt"), "alt");
  }

  @Test
  public void testInsertRequiredAltImgJS_noWarning() {
    checkInspectionDoesNotExist("insertRequiredAltImg_after.js",
                                XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "alt"), "alt");
  }

  @Test
  public void testInsertRequiredLangHtml() {
    checkInspection("insertRequiredLang_before.html", "insertRequiredLang_after.html", "lang",
                    XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "lang"));
    checkInspectionDoesNotExist("insertRequiredLang_before.html",
                                XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "xml:lang"), "lang");
  }

  @Test
  public void testInsertRequiredLangXhtml_xmllang() {
    checkInspection("insertRequiredLang_before.xhtml", "insertRequiredXmllang_after.xhtml", "lang",
                    XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "xml:lang"));
  }

  @Test
  public void testInsertRequiredLangXhtml11() {
    checkInspection("insertRequiredLang_xhtml11_before.xhtml", "insertRequiredLang_xhtml11_after.xhtml", "lang",
                    XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "xml:lang"));
    checkInspectionDoesNotExist("insertRequiredLang_xhtml11_before.xhtml",
                                XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "lang"), "lang");
  }

  @Test
  public void testInsertRequiredTitleElement_html() {
    checkInspection("InsertRequiredTitleElement_before.html", "InsertRequiredTitleElement_after.html", "title",
                    HtmlToolsBundle.message("html.intention.create.sub.element.text", "title"));
  }

  @Test
  public void testInsertRequiredTitleElement_htmlMixedCase() {
    checkInspection("InsertRequiredTitleElementMixedCase_before.html", "InsertRequiredTitleElementMixedCase_after.html", "title",
                    HtmlToolsBundle.message("html.intention.create.sub.element.text", "title"));
  }

  @Test
  public void testInsertRequiredTitleElement_xhtml() {
    checkInspection("InsertRequiredTitleElement_before.xhtml", "InsertRequiredTitleElement_after.xhtml", "title",
                    HtmlToolsBundle.message("html.intention.create.sub.element.text", "title"));
  }

  @Test
  public void testInsertRequiredTitleElement_nowarnings_xhtml() {
    checkInspectionDoesNotExist("InsertRequiredTitleElement_nowarnings.xhtml",
                                HtmlToolsBundle.message("html.intention.create.sub.element.text", "title"), "title");
  }

  @Test
  public void testInsertRequiredTitleElement_nowarnings_html() {
    checkInspectionDoesNotExist("InsertRequiredTitleElement_nowarnings.html",
                                HtmlToolsBundle.message("html.intention.create.sub.element.text", "title"), "title");
  }

  @Test
  public void testInsertRequiredSummary() {
    checkInspection("InsertRequiredSummary_before.html", "InsertRequiredSummary_after.html", "summary",
                    XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "summary"));
  }

  private void checkInspection(String fileNameBefore, String fileNameAfter, String folder, String intention) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + "/" + folder + "/" + fileNameBefore);
    final List<IntentionAction> list = myFixture.getAvailableIntentions();
    final IntentionAction action = CodeInsightTestUtil.findIntentionByPartialText(list, intention);
    assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResultByFile(BASE_PATH + "/" + folder + "/" + fileNameAfter);
  }

  private void checkInspectionDoesNotExist(String fileNameBefore, String intention, String folder) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + "/" + folder + "/" + fileNameBefore);
    final List<IntentionAction> list = myFixture.getAvailableIntentions();
    final IntentionAction action = CodeInsightTestUtil.findIntentionByPartialText(list, intention);
    assertNull(action);
  }

  private HtmlCodeStyleSettings getHtmlCodeStyleSettings() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    return CodeStyle.getSettings(myFixture.getProject()).getCustomSettings(HtmlCodeStyleSettings.class);
  }

  private String getTestName(boolean lowercaseFirstLetter) {
    return PlatformTestUtil.getTestName(myTestMethodName, lowercaseFirstLetter);
  }
}

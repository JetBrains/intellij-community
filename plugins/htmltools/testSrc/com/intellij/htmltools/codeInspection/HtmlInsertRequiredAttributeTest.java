package com.intellij.htmltools.codeInspection;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlRequiredAltAttributeInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlRequiredLangAttributeInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlRequiredSummaryAttributeInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlRequiredTitleElementInspection;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HtmlInsertRequiredAttributeTest extends LightQuickFixTestCase {

  public static final String BASE_PATH = "/insertRequiredAttribute";

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new RequiredAttributesInspection(),
      new HtmlRequiredAltAttributeInspection(),
      new HtmlRequiredLangAttributeInspection(),
      new HtmlRequiredTitleElementInspection(),
      new HtmlRequiredSummaryAttributeInspection()
    };
  }


  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/htmltools/testData/inspections";
  }

  public void testInsertDefaultAttribute() {
    doSingleTest(getTestName(false) + ".html");
  }

  public void testDefaultWithChangedQuote() {
    final HtmlCodeStyleSettings settings = getHtmlCodeStyleSettings();
    final CodeStyleSettings.QuoteStyle oldQuote = settings.HTML_QUOTE_STYLE;
    try {
      settings.HTML_QUOTE_STYLE = CodeStyleSettings.QuoteStyle.Single;
      doSingleTest(getTestName(false) + ".html");
    }
    finally {
      settings.HTML_QUOTE_STYLE = oldQuote;
    }
  }

  public void testInsertRequiredAltInput() {
    checkInspection("insertRequiredAltInput_before.html", "insertRequiredAltInput_after.html", "alt",
                    XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "alt"));
  }

  public void testInsertRequiredAltArea() {
    checkInspection("insertRequiredAltArea_before.html", "insertRequiredAltArea_after.html", "alt",
                    XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "alt"));
  }

  public void testInsertRequiredAltImg() {
    checkInspection("insertRequiredAltImg_before.html", "insertRequiredAltImg_after.html", "alt",
                    XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "alt"));
  }
  public void testInsertRequiredAltImg_noWarning() {
    checkInspectionDoesNotExist("insertRequiredAltImg_after.html",
                                XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "alt"), "alt");
  }

  public void testInsertRequiredAltImgVue_noWarning() {
    checkInspectionDoesNotExist("insertRequiredAltImg_after.vue",
                                XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "alt"), "alt");
  }

  public void testInsertRequiredAltImgJS_noWarning() {
    checkInspectionDoesNotExist("insertRequiredAltImg_after.js",
                                XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "alt"), "alt");
  }

  public void testInsertRequiredLangHtml() {
    checkInspection("insertRequiredLang_before.html", "insertRequiredLang_after.html", "lang",
                    XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "lang"));
    checkInspectionDoesNotExist("insertRequiredLang_before.html",
                                XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "xml:lang"), "lang");
  }

  public void testInsertRequiredLangXhtml_xmllang() {
    checkInspection("insertRequiredLang_before.xhtml", "insertRequiredXmllang_after.xhtml", "lang",
                    XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "xml:lang"));
  }

  public void testInsertRequiredLangXhtml11() {
    checkInspection("insertRequiredLang_xhtml11_before.xhtml", "insertRequiredLang_xhtml11_after.xhtml", "lang",
                    XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "xml:lang"));
    checkInspectionDoesNotExist("insertRequiredLang_xhtml11_before.xhtml",
                                XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "lang"), "lang");
  }

  public void testInsertRequiredTitleElement_html() {
    checkInspection("InsertRequiredTitleElement_before.html", "InsertRequiredTitleElement_after.html", "title",
                    HtmlToolsBundle.message("html.intention.create.sub.element.text", "title"));
  }

  public void testInsertRequiredTitleElement_htmlMixedCase() {
    checkInspection("InsertRequiredTitleElementMixedCase_before.html", "InsertRequiredTitleElementMixedCase_after.html", "title",
                    HtmlToolsBundle.message("html.intention.create.sub.element.text", "title"));
  }

  public void testInsertRequiredTitleElement_xhtml() {
    checkInspection("InsertRequiredTitleElement_before.xhtml", "InsertRequiredTitleElement_after.xhtml", "title",
                    HtmlToolsBundle.message("html.intention.create.sub.element.text", "title"));
  }

  public void testInsertRequiredTitleElement_nowarnings_xhtml() {
    checkInspectionDoesNotExist("InsertRequiredTitleElement_nowarnings.xhtml",
                                HtmlToolsBundle.message("html.intention.create.sub.element.text", "title"), "title");
  }

  public void testInsertRequiredTitleElement_nowarnings_html() {
    checkInspectionDoesNotExist("InsertRequiredTitleElement_nowarnings.html",
                                HtmlToolsBundle.message("html.intention.create.sub.element.text", "title"), "title");
  }

  public void testInsertRequiredSummary() {
    checkInspection("InsertRequiredSummary_before.html", "InsertRequiredSummary_after.html", "summary",
                    XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", "summary"));
  }

  private void checkInspection(String fileNameBefore, String fileNameAfter, String folder, String intention) {
    configureByFile(BASE_PATH + "/" + folder + "/" + fileNameBefore);
    final List<IntentionAction> list = getAvailableActions();
    final IntentionAction action = CodeInsightTestUtil.findIntentionByPartialText(list, intention);
    assertNotNull(action);
    invoke(action);
    checkResultByFile(BASE_PATH + "/" + folder + "/" + fileNameAfter);
  }

  private void checkInspectionDoesNotExist(String fileNameBefore, String intention, String folder) {
    configureByFile(BASE_PATH + "/" + folder + "/" + fileNameBefore);
    final List<IntentionAction> list = getAvailableActions();
    final IntentionAction action = CodeInsightTestUtil.findIntentionByPartialText(list, intention);
    assertNull(action);
  }

  private HtmlCodeStyleSettings getHtmlCodeStyleSettings() {
    return CodeStyle.getSettings(getProject()).getCustomSettings(HtmlCodeStyleSettings.class);
  }
}

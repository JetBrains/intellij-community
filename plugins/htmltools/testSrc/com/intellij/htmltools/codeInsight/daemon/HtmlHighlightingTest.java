package com.intellij.htmltools.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.daemon.impl.analysis.HtmlUnknownAnchorTargetInspection;
import com.intellij.codeInsight.daemon.impl.analysis.HtmlUnknownTargetInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnboundNsPrefixInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.htmlInspections.HtmlExtraClosingTagInspection;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownAttributeInspection;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownBooleanAttributeInspection;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownTagInspection;
import com.intellij.codeInspection.htmlInspections.HtmlWrongAttributeValueInspection;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.codeInspection.htmlInspections.SwitchToHtml5WithHighPriorityAction;
import com.intellij.codeInspection.htmlInspections.XmlWrongRootElementInspection;
import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection;
import com.intellij.htmltools.HtmlToolsTestsUtil;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlDeprecatedAttributeInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlDeprecatedTagInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlNonExistentInternetResourceInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlPresentationalElementInspection;
import com.intellij.htmltools.lang.annotation.HtmlNonExistentInternetResourcesAnnotator;
import com.intellij.htmltools.xml.util.CheckImageSizeInspection;
import com.intellij.ide.highlighter.HtmlHighlighterFactory;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.ExternalResourceManagerExBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.HighlightTestInfo;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.psi.XmlPsiBundle;
import com.intellij.xml.util.CheckDtdReferencesInspection;
import com.intellij.xml.util.CheckEmptyTagInspection;
import com.intellij.xml.util.CheckValidXmlInScriptBodyInspection;
import com.intellij.xml.util.CheckXmlFileWithXercesValidatorInspection;
import com.intellij.xml.util.XmlDuplicatedIdInspection;
import com.intellij.xml.util.XmlInvalidIdInspection;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Maxim.Mossienko
 */
@TestApplication
@TestDataPath("$PROJECT_ROOT/community/plugins/htmltools/testData/highlighting")
public class HtmlHighlightingTest {
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
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    myFixture.enableInspections(inspectionTools());
  }

  public static LocalInspectionTool @NotNull [] inspectionTools() {
    return new LocalInspectionTool[]{
      new RequiredAttributesInspection(),
      new HtmlExtraClosingTagInspection(),
      new HtmlUnknownAttributeInspection(),
      new HtmlUnknownTagInspection(),
      new HtmlUnknownBooleanAttributeInspection(),
      new HtmlWrongAttributeValueInspection(),
      new XmlWrongRootElementInspection(),
      new XmlDuplicatedIdInspection(),
      new XmlInvalidIdInspection(),
      new CheckXmlFileWithXercesValidatorInspection(),
      new XmlUnboundNsPrefixInspection(),
      new HtmlPresentationalElementInspection(),
      new HtmlUnknownTargetInspection(),
      new HtmlUnknownAnchorTargetInspection(),
      new XmlUnresolvedReferenceInspection()
    };
  }

  @AfterEach
  void tearDown() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    InspectionProfileImpl.INIT_INSPECTIONS = false;
    ExternalResourceManagerEx.getInstanceEx().setDefaultHtmlDoctype(myOldDoctype, myFixture.getProject());
  }

  @Test
  public void testemptyAttr() {
    doTest();
  }

  @Test
  public void testcaseInAttrValue() {
    doTest();
  }

  @Test
  public void testcaseInTagAndAttr() {
    doTest();
  }

  @Test
  public void testNoEndTag() {
    doTest();
  }

  @Test
  public void testNoEndTag2() {
    doTest();
  }

  @Test
  public void testAboutScheme() {
    doTest();
  }

  @Test
  public void testIdHtml4() {
    doTest();
  }

  @Test
  public void testIdHtml4Implicit() {
    doTest();
  }

  @Test
  public void testIdHtml5() {
    doTest();
  }

  @Test
  public void testIdHtml5Implicit() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    String defaultHtmlDoctype = manager.getDefaultHtmlDoctype(myFixture.getProject());
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), myFixture.getProject());
    try {
      doTest();
    }
    finally {
      manager.setDefaultHtmlDoctype(defaultHtmlDoctype, myFixture.getProject());
    }
  }

  @Test
  public void testNoEndTag3() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(getTestName(false) + ".html");
    myFixture.checkHighlighting(true, false, true);
    IntentionAction intention = myFixture.findSingleIntention("Remove extra closing tag");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + "_after.html");
  }

  @Test
  public void testNoEndTag4() {
    doTest();
  }

  @Test
  public void testNsDef() {
    doTest();
  }

  @Test
  public void testExtraEndTag() {
    doTest();
  }

  @Test
  public void testHtmlWithStrictDtd() {
    doTest();
  }

  @Test
  public void testHtml5() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyDirectoryToProject("html5", "html5");
    myFixture.configureByFile("html5/" + getTestName(false) + "." + "html");
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testHtml5_2() {
    doTest();
  }

  @Test
  public void testXhtml5() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyDirectoryToProject("html5", "html5");
    myFixture.configureByFiles("html5/" + getTestName(false) + "." + "xhtml");
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testHtml5_3() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    String defaultHtmlDoctype = manager.getDefaultHtmlDoctype(myFixture.getProject());
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), myFixture.getProject());
    try {
      myFixture.copyDirectoryToProject("html5", "html5");
      myFixture.configureByFiles("html5/" + getTestName(false) + "." + "html");
      myFixture.checkHighlighting(true, false, false);
    }
    finally {
      manager.setDefaultHtmlDoctype(defaultHtmlDoctype, myFixture.getProject());
    }
  }

  @Test
  public void testHtml5_4() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new HtmlDeprecatedTagInspection());
    doTest();
  }

  @Test
  public void testSvgAndMathMl() {
    doTest();
  }

  @Test
  public void testSvgAndMathMlNoNs() {
    doTest();
  }

  @Test
  public void testFullSvg() {
    doTest();
  }

  @Test
  public void testRDFa() {
    doTest();
  }

  @Test
  public void testARIA() {
    doTest();
  }

  @Test
  public void testXhtml5_2() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    String defaultHtmlDoctype = manager.getDefaultHtmlDoctype(myFixture.getProject());
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), myFixture.getProject());
    try {
      myFixture.copyDirectoryToProject("html5",  "html5");
      myFixture.configureByFiles("html5/" + getTestName(false) + "." + "xhtml");
      myFixture.checkHighlighting(true, false, false);
    }
    finally {
      manager.setDefaultHtmlDoctype(defaultHtmlDoctype, myFixture.getProject());
    }
  }

  @Test
  public void testHtml5DataAttrs() {
    doTest();
  }

  @Test
  public void testHtml5DataAttrs1() {
    doTest();
  }

  @Test
  public void testHtml5DataAttrs2() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    String defaultHtmlDoctype = manager.getDefaultHtmlDoctype(myFixture.getProject());
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), myFixture.getProject());
    try {
      doTest();
    }
    finally {
      manager.setDefaultHtmlDoctype(defaultHtmlDoctype, myFixture.getProject());
    }
  }

  @Test
  public void testHtml5Equiv() {
    //WEB-5171
    doTest();
  }

  @Test
  public void testXhtml5DataAttrs() {
    doTest("xhtml");
  }

  @Test
  public void testXhtml5DataAttrs1() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    String defaultHtmlDoctype = manager.getDefaultHtmlDoctype(myFixture.getProject());
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), myFixture.getProject());
    try {
      doTest("xhtml");
    }
    finally {
      manager.setDefaultHtmlDoctype(defaultHtmlDoctype, myFixture.getProject());
    }
  }

  @Test
  public void testNoRootHtmlAndHeadAndBody() {
    doTest();
  }

  @Test
  public void testNoRootHtmlTag() {
    doTest();
  }

  @Test
  public void testHtmlNoBody() {
    doTest();
  }

  @Test
  public void testHtmlNoHead() {
    doTest();
  }

  @Test
  public void testHtmlNoHeadBody() {
    doTest();
  }

  @Test
  public void testHtml4NoBody() {
    doTest();
  }

  @Test
  public void testHtml4NoHead() {
    doTest();
  }

  @Test
  public void testHtml4NoHeadBody() {
    doTest();
  }

  @Test
  public void testFrameSet() {
    doTest();
  }

  @Test
  public void testIdValidation() {
    doTest();
  }

  @Test
  public void testAnchors() {
    doTest();
  }

  @Test
  public void testBadAnchor() {
    doTest();
  }

  @Test
  public void testLinks() {
    doTest();
  }

  @Test
  public void testJSInOnTags() {
    doTest();
  }

  @Test
  public void testFixedAttributeValues() {
    doTest();
  }

  @Test
  public void testUnknownHrefTarget() {
    doTest();
  }

  @Test
  public void testUnknownHrefTarget1() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    HtmlUnknownTargetInspection inspection = new HtmlUnknownTargetInspection();
    myFixture.disableInspections(inspection);
    doTest();
  }

  @Test
  public void testTelAndSmsHrefPrefixes() {
    doTest();
  }

  @Test
  public void testXhtmlMobile() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    HtmlUnknownTargetInspection inspection = new HtmlUnknownTargetInspection();
    myFixture.disableInspections(inspection);
    doTest();
  }

  @Test
  public void testWrongRootElementDoNotValidate() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(getTestName(false) + ".html");
    myFixture.checkHighlighting(true, true, false);
  }

  @Test
  public void testLinksWithSpaces() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    if (HtmlToolsTestsUtil.isCommunityContext()) return;
    myFixture.configureByFiles("/html file.html", "/Links.html");
    myFixture.checkHighlighting(true, true, false);
  }

  @Test
  public void testpClosedByBlockElement() {
    doTest();
  }

  @Test
  public void testWrongRootElement() {
    doTest("xhtml");
  }

  @Test
  public void testReferencingDirsWithLinkEndingWithSlash() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(getTestName(false) + ".html");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      myFixture.getFile().getContainingDirectory().createSubdirectory("aaa");
    });

    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testValidationOfSingleTag() {
    doTest();
  }

  @Test
  public void testDocTypeAndRootTagInUpperCase() {
    doTest();
  }

  @Test
  public void testOnlyDocType() {
    doTest();
  }

  @Test
  public void testExternalValidationForXHtml() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(getTestName(false) + ".xhtml");
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testAttributeValidationRelaxation() {
    doTest();
  }

  @Test
  public void testIncompleteDoctype() {
    doTest();
  }

  @Test
  public void testSOE() {
    doTest();
  }

  @Test
  public void testSOEFromRelaxedDescriptor() {
    if (HtmlToolsTestsUtil.isCommunityContext()) return;
    doTest("xhtml");
  }

  @Test
  public void testXIncludeHRef() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.disableInspections(new CheckXmlFileWithXercesValidatorInspection());
    final String testName = getTestName(false);
    final String location = "XInclude.xsd";
    final String url = "http://www.w3.org/2001/XInclude";

    ExternalResourceManagerExBase.registerResourceTemporarily(url, location, myFixture.getTestRootDisposable());

    myFixture.configureByFiles(testName + ".xhtml", location);
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testCheckImageSizeInspection() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyFileToProject(getTestName(false) + ".gif");
    myFixture.copyFileToProject(getTestName(false) + ".svg");
    myFixture.configureByFiles(getTestName(false) + ".html");

    CheckImageSizeInspection tool = new CheckImageSizeInspection();
    myFixture.enableInspections(tool);
    myFixture.checkHighlighting(true, false, false);

    myFixture.configureByFiles(getTestName(false) + "5.html");
    myFixture.checkHighlighting(true, false, false);

    if (!HtmlToolsTestsUtil.isCommunityContext()) {
      myFixture.configureByFiles(getTestName(false) + ".jsp");
      myFixture.checkHighlighting(true, false, false);
    }

    myFixture.configureByFiles(getTestName(false) + "DataUri.html");
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testCheckValidXmlInScriptBodyInspection() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(
      getTestName(false) + ".xhtml"
    );

    CheckValidXmlInScriptBodyInspection tool = new CheckValidXmlInScriptBodyInspection();
    myFixture.enableInspections(tool);
    if (!HtmlToolsTestsUtil.isCommunityContext()) {
      myFixture.checkHighlighting(true, false, false);

      myFixture.configureByFile(
        getTestName(false) + "2.xhtml"
      );
      myFixture.checkHighlighting(true, false, false);
    }

    myFixture.configureByFile(
      getTestName(false) + ".html"
    );
    myFixture.checkHighlighting(true, false, false);

    myFixture.configureByFile(
      getTestName(false) + "2.html"
    );
    myFixture.checkHighlighting(true, false, false);

    myFixture.configureByFile(
      getTestName(false) + "3.html"
    );
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testDifferentCaseInAttrs() {
    doTest();
  }

  @Test
  public void testMapIdRequired() {
    doTest();
  }

  @Test
  public void testUseMap() {
    doTest();
  }

  @Test
  public void testSelectHasNoWidth() {
    doTest();
  }

  @Test
  public void testRelativeProtocolUrls() {
    doTest();
  }

  @Test
  public void testReportAboutEmptyEnd() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new CheckEmptyTagInspection());
    doTest();
  }

  @Test
  public void testXHtml11() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(getTestName(false) + ".xhtml");
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testEntities() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest();
    myFixture.configureByFile(getTestName(false) + ".xhtml");
    myFixture.checkHighlighting(true, false, false);
    myFixture.configureByFile(getTestName(false) + "2.xhtml");
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testEditorHighlighting() {
    //                            10           20
    //                   012345678901 2 345678901234567890
    String text = "<xxx aaa = \"\"/> <xxx/>";
    EditorHighlighter htmlHighlighter = HtmlHighlighterFactory.createHTMLHighlighter(EditorColorsManager.getInstance().getGlobalScheme());
    htmlHighlighter.setText(text);

    HighlighterIterator iterator = htmlHighlighter.createIterator(4);
    assertEquals(XmlTokenType.TAG_WHITE_SPACE, iterator.getTokenType());

    iterator = htmlHighlighter.createIterator(8);
    assertEquals(XmlTokenType.TAG_WHITE_SPACE, iterator.getTokenType());

    iterator = htmlHighlighter.createIterator(10);
    assertEquals(XmlTokenType.TAG_WHITE_SPACE, iterator.getTokenType());

    iterator = htmlHighlighter.createIterator(15);
    assertEquals(XmlTokenType.XML_REAL_WHITE_SPACE, iterator.getTokenType());

    //                10         20
    //      01234567890123456 7890 1234567890
    text = "<?xml version = \"1.0\"?> <html/>";
    htmlHighlighter = HtmlHighlighterFactory.createHTMLHighlighter(EditorColorsManager.getInstance().getGlobalScheme());
    htmlHighlighter.setText(text);

    iterator = htmlHighlighter.createIterator(1);
    assertEquals(XmlTokenType.XML_PI_START, iterator.getTokenType());

    iterator = htmlHighlighter.createIterator(10);
    assertEquals(XmlTokenType.XML_PI_TARGET, iterator.getTokenType());

    iterator = htmlHighlighter.createIterator(22);
    assertEquals(XmlTokenType.XML_PI_END, iterator.getTokenType());

    //                 10        20        30        40        50         60           70
    //      012345678 901234567890123456789012345678901234567890123456 789012 345 678 90
    text = "<tr><td>\\${param.foo}</td><td>${param.foo}</td><td name=\"${el['el']}\"/></tr>";
    htmlHighlighter.setText(text);

    iterator = htmlHighlighter.createIterator(10);
    assertSame(XmlTokenType.XML_DATA_CHARACTERS, iterator.getTokenType(), "Escaped el");
    iterator = htmlHighlighter.createIterator(30);
    assertSame(XmlTokenType.XML_DATA_CHARACTERS, iterator.getTokenType(), "El start");
    iterator = htmlHighlighter.createIterator(35);
    assertSame(XmlTokenType.XML_DATA_CHARACTERS, iterator.getTokenType(), "El identifier");

    iterator = htmlHighlighter.createIterator(37);
    assertSame(XmlTokenType.XML_DATA_CHARACTERS, iterator.getTokenType(), "El dot");
    iterator = htmlHighlighter.createIterator(41);
    assertSame(XmlTokenType.XML_DATA_CHARACTERS, iterator.getTokenType(), "El end");

    iterator = htmlHighlighter.createIterator(57);
    assertSame(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, iterator.getTokenType(), "El start");
    iterator = htmlHighlighter.createIterator(61);
    assertSame(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, iterator.getTokenType(), "El lbracket");
    iterator = htmlHighlighter.createIterator(63);
    assertSame(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, iterator.getTokenType(), "El string literal");

    iterator = htmlHighlighter.createIterator(67);
    assertSame(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, iterator.getTokenType(), "El end");
    iterator = htmlHighlighter.createIterator(68);
    assertSame(XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER, iterator.getTokenType(), "Attribute value end");

    //                10        20        30
    //      0123456789012345678901234567890123456
    text = "<!--- bla --->";
    htmlHighlighter.setText(text);

    iterator = htmlHighlighter.createIterator(6);
    assertSame(XmlTokenType.XML_COMMENT_CHARACTERS, iterator.getTokenType(), "Comment content");
    iterator = htmlHighlighter.createIterator(12);
    assertSame(XmlTokenType.XML_COMMENT_END, iterator.getTokenType(), "Comment end");

    //                10          20        30
    //      012345678901234 5 678901234567890123456
    text = "<input onclick=\"\"/> <a></a>";
    htmlHighlighter.setText(text);
    iterator = htmlHighlighter.createIterator(19);
    assertEquals(XmlTokenType.XML_REAL_WHITE_SPACE, iterator.getTokenType());

    //                10        20        30
    //      0123456789012345678901234567890123456
    text = "<#input tableRow as value></#input>";
    htmlHighlighter.setText(text);
    iterator = htmlHighlighter.createIterator(3);
    assertEquals(XmlTokenType.XML_DATA_CHARACTERS, iterator.getTokenType());

    //                10
    //      01234567890123456789
    text = "<!--&nbsp; &aaa;-->";

    htmlHighlighter.setText(text);
    iterator = htmlHighlighter.createIterator(5);
    assertEquals(
      XmlTokenType.XML_COMMENT_CHARACTERS,
      iterator.getTokenType(),
      "char entity in comment"
    );

    iterator = htmlHighlighter.createIterator(12);
    assertEquals(
      XmlTokenType.XML_COMMENT_CHARACTERS,
      iterator.getTokenType(),
      "entity ref in comment"
    );

    //                10
    //      01234567890123456789
    text = "<div> < in </div>";
    htmlHighlighter.setText(text);
    iterator = htmlHighlighter.createIterator(6);
    assertEquals(
      XmlTokenType.XML_DATA_CHARACTERS,
      iterator.getTokenType(),
      "< before space should be tag data"
    );

    iterator = htmlHighlighter.createIterator(8);
    assertEquals(
      XmlTokenType.XML_DATA_CHARACTERS,
      iterator.getTokenType(),
      "should be tag data"
    );
  }

  @Test
  public void testDeprecatedAndPresentationalTags() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new HtmlDeprecatedTagInspection());
    doTest();
  }

  @Test
  public void testDeprecatedAttributes() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new HtmlDeprecatedAttributeInspection());
    doTest();
  }

  @Test
  public void testDeprecatedAndPresentationalTagsHtml5() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new HtmlDeprecatedTagInspection());
    doTest();
  }

  @Test
  public void testMenuHtml5TagNotDeprecated() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new HtmlDeprecatedTagInspection());
    doTest();
  }

  @Test
  public void testPresentationalTagInHtml5() {
    doTest();
  }

  @Test
  public void testPresentationalTagInXhtml5() {
    doTest("xhtml");
  }

  @Test
  public void testHtmlPsi() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(getTestName(false) + ".html");
    ReadAction.run(() -> {
      final XmlTag tag = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()), XmlTag.class);
      assertNotNull(tag);
      assertNull(tag.getAttribute("type", ""));
      assertNotNull(tag.getAttribute("type", "http://tapestry.apache.org/schema/tapestry_5_0_0.xsd"));
      assertNotNull(tag.getAttribute("t:type", ""));
      assertNotNull(tag.getAttribute("type", "http://tapestry.apache.org/schema/tapestry_5_0_0.xsd"));
    });
  }

  @Test
  public void testParametrizedReference() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles(getTestName(false) + ".html",
                               getTestName(false) + ".css");
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testAddCustomTagFixTest() {
    doTestWithAddToCustomsQuickFix("Add zzz to custom html tags");
  }

  @Test
  public void testAddCustomTagFixTest2() {
    doTestWithAddToCustomsQuickFix("Add abba to custom html tags", 2);
  }

  @Test
  public void testAddCustomAttributeFixTest() {
    doTestWithAddToCustomsQuickFix("Add zzz to custom html attributes");
  }

  @Test
  public void testAddCustomBooleanAttribute() {
    doTestWithAddToCustomsQuickFix(XmlAnalysisBundle.message("html.quickfix.add.custom.html.boolean.attribute", "title"));
  }

  @Test
  public void testAddNotRequiredAttributeFixTest() {
    doTestWithAddToCustomsQuickFix("Add alt to not required html attributes");
  }

  @Test
  public void testUnknownBooleanAttributeWithAddAttributeValueQuickFix() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(getTestName(false) + ".html");
    myFixture.checkHighlighting(true, false, true);
    IntentionAction intention = myFixture.findSingleIntention(XmlPsiBundle.message("xml.quickfix.add.attribute.value.text"));
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + "_after.html");
  }

  @Test
  public void testDataUri() {
    doTest();
  }

  @Test
  public void testHtml5QuickFixShouldBeFirst() {
    doTestHtml5QuickFixShouldBeFirst();
  }

  @Test
  public void testHtml5QuickFixShouldBeFirst1() {
    doTestHtml5QuickFixShouldBeFirst();
  }

  @Test
  public void testPlaceChecking() {
    doTest();
  }

  @Test
  public void testHtml5Entities() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new CheckDtdReferencesInspection());
    doTest();
  }

  @Test
  public void testHtml5EmptyAttr() {
    doTest();
  }

  @Test
  public void testHtml5Autocomplete() {
    doTest();
  }

  @Test
  public void testHtml5EntitiesWithoutRootTag() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    // restore HTML5 doctype, because we're unable to determine it without doctype
    ExternalResourceManagerEx.getInstanceEx().setDefaultHtmlDoctype(myOldDoctype, myFixture.getProject());
    myFixture.enableInspections(new CheckDtdReferencesInspection());
    doTest();
  }

  @Test
  public void testWebLinksValidator1() throws Exception {
    doTestWebLinks(false);
  }

  @Test
  public void testWebLinksValidator2() throws Exception {
    try (ServerSocket socket = new ServerSocket(1118)) {
      doTestWebLinks(false);
    }
  }

  @Test
  public void testWebLinksValidator3() throws Exception {
    doTestWebLinks(true);
  }

  @Test
  public void testWebLinksValidator4() throws Exception {
    doTestWebLinks(true);
  }

  @Test
  public void testWebLinksValidator5() throws Exception {
    doTestWebLinks(true);
  }

  @Test
  public void testWebLinksValidator6() throws Exception {
    doTestWebLinks(true);
  }

  @Test
  public void testWebLinksValidator7() throws Exception {
    doTestWebLinks(true);
  }

  @Test
  public void testWebLinksValidator8() throws Exception {
    doTestWebLinks(true);
  }

  @Test
  public void testWI5823() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    String defaultHtmlDoctype = manager.getDefaultHtmlDoctype(myFixture.getProject());
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), myFixture.getProject());
    try {
      doTest();
    }
    finally {
      manager.setDefaultHtmlDoctype(defaultHtmlDoctype, myFixture.getProject());
    }
  }

  @Test
  public void testWEB5171() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new HtmlUnknownAttributeInspection());
    doTest();
  }

  @Test
  public void testWEB7422() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new HtmlUnknownAttributeInspection());
    doTest();
  }

  @Test
  public void testWEB404() {
    doTest();
  }

  @Test
  public void testWEB8397() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new HtmlUnknownTagInspection());
    doTest();
  }

  @Test
  public void testHtml5NonW3C() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    // if you see this test fail, please make sure that all attributes/elements containing nonW3C
    // in the declaration were removed from the schema
    // please update this test with all attributes/elements you remove this way
    myFixture.enableInspections(new HtmlDeprecatedTagInspection());
    doTest();
  }

  @Test
  public void testImgLoading() {
    doTest();
  }

  @Test
  public void testSpelling() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new GrazieSpellCheckingInspection());
    doTest();
  }

  @Test
  public void testBase() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyFileToProject("CheckImageSizeInspection.gif", "images/CheckImageSizeInspection.gif");
    myFixture.configureByFile("/Base.html");
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testBaseUrl() {
    doTest();
  }

  @Test
  public void testCustomTagHighlighting() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    HtmlUnknownTagInspection inspection = new HtmlUnknownTagInspection();
    inspection.updateAdditionalEntries("custom-tag,custom2-TAG", myFixture.getTestRootDisposable());
    myFixture.enableInspections(inspection);

    HighlightTestInfo info = myFixture.testFile(getTestName(false) + ".html");
    info.checkSymbolNames();
    info.test();
  }

  private void doTestWebLinks(boolean startTestingLocalServer) throws Exception {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final MyTestingServer server = new MyTestingServer();
    HtmlNonExistentInternetResourceInspection inspection = new HtmlNonExistentInternetResourceInspection();
    try {
      HtmlNonExistentInternetResourcesAnnotator.ourEnableInTestMode = true;
      myFixture.enableInspections(inspection);

      if (startTestingLocalServer) {
        server.start();
      }
      doTest();
    }
    finally {
      HtmlNonExistentInternetResourcesAnnotator.ourEnableInTestMode = false;
      myFixture.disableInspections(inspection);

      if (startTestingLocalServer) {
        server.stop();
      }
    }
  }

  private void doTestHtml5QuickFixShouldBeFirst() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(getTestName(false) + ".html");
    myFixture.doHighlighting();
    ShowIntentionsPass.IntentionsInfo intentions = EdtTestUtil.runInEdtAndGet(() -> ProgressManager.getInstance().runProcess(()->ShowIntentionsPass.getActionsToShow(myFixture.getEditor(), myFixture.getFile()), new EmptyProgressIndicator()));
    assertFalse(intentions.isEmpty());
    CachedIntentions actions = EdtTestUtil.runInEdtAndGet(() -> CachedIntentions.createAndUpdateActions(myFixture.getProject(), myFixture.getFile(), myFixture.getEditor(), intentions));
    List<IntentionActionWithTextCaching> allActions = ReadAction.compute(() -> actions.getAllActions());
    assertThat(allActions).isNotEmpty();
    String firstActionText = ReadAction.compute(() -> allActions.get(0).getAction().getText());
    assertEquals(new SwitchToHtml5WithHighPriorityAction().getText(), firstActionText);
  }

  private void doTestWithAddToCustomsQuickFix(String intentionName) {
    doTestWithAddToCustomsQuickFix(intentionName, 1);
  }

  private void doTestWithAddToCustomsQuickFix(String intentionName, final int fixesNum) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String s = getTestName(false);
    myFixture.configureByFile(s + ".html");
    Collection<HighlightInfo> highlightInfos = myFixture.doHighlighting();
    assertEquals(fixesNum, highlightInfos.size());
    IntentionAction intention = myFixture.findSingleIntention(intentionName);
    myFixture.launchAction(intention);
    assertEquals(0, myFixture.doHighlighting().stream().filter(el -> el.type != HighlightInfoType.INFORMATION).count());
  }

  protected void doTest(String ext) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(getTestName(false) + "." + ext);
    myFixture.checkHighlighting(true, false, false);
  }

  protected void doTest() {
    doTest("html");
  }

  private static class MyTestingServer {
    private volatile boolean myServerStopped = false;
    private volatile boolean myServerStarted = false;
    private volatile boolean mySocketClosed = false;

    private static final int PORT = 1118;
    private final Object myLock = new Object();
    private Future<?> myThread;

    public void start() throws IOException {
      assertTrue(checkPort(PORT));

      myThread = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            serverSocket.setSoTimeout(500);
            while (!myServerStopped) {
              try {
                synchronized (myLock) {
                  myServerStarted = true;
                  myLock.notifyAll();
                }

                try (Socket clientSocket = serverSocket.accept()) {
                  clientSocket.setSoTimeout(500);
                  final String httpRequest = readLine(clientSocket);

                  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
                  final PrintWriter writer = new PrintWriter(clientSocket.getOutputStream());

                  if (httpRequest.contains("nonexistent-resource")) {
                    writer.println("HTTP/1.0 404 Not Found\n");
                  }
                  else if (httpRequest.contains("gone-resource")) {
                    writer.println("HTTP/1.0 410 Gone\n");
                  }
                  else if (httpRequest.contains("forbidden-resource")) {
                    writer.println("HTTP/1.0 403 Forbidden\n");
                  }
                  else if (httpRequest.contains("error-resource")) {
                    writer.println("HTTP/1.0 500 Internal Server Error\n");
                  }
                  else {
                    writer.println("HTTP/1.0 200 OK\n");
                  }

                  writer.flush();
                }
              }
              catch (SocketTimeoutException ignored) {
              }
            }
          }
          finally {
            synchronized (myLock) {
              mySocketClosed = true;
              myLock.notifyAll();
            }
          }
        }
        catch (IOException e) {
          synchronized (myLock) {
            mySocketClosed = true;
            myLock.notifyAll();
          }
          throw new RuntimeException(e);
        }
      });

      synchronized (myLock) {
        while (!myServerStarted) {
          try {
            myLock.wait();
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      }

      try {
        Thread.sleep(200);
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    private static String readLine(@NotNull Socket socket) throws IOException {
      final InputStreamReader reader = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
      @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
      final BufferedReader bufferedReader = new BufferedReader(reader);
      return bufferedReader.readLine();
    }

    private static boolean checkPort(int port) throws IOException {
      try (ServerSocket serverSocket = new ServerSocket(port)) {
        serverSocket.setSoTimeout(10);
        @SuppressWarnings("SocketOpenedButNotSafelyClosed") final Socket clientSocket = serverSocket.accept();
        clientSocket.setSoTimeout(500);
        clientSocket.close();
      }
      catch (SocketTimeoutException ignored) {
      }
      catch (IOException e) {
        return false;
      }
      return true;
    }

    public void stop() throws InterruptedException, ExecutionException {
      if (!myServerStarted) {
        return;
      }

      myServerStopped = true;
      synchronized (myLock) {
        while (!mySocketClosed) {
          try {
            myLock.wait();
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      }
      myThread.get();
    }
  }

  private String getTestName(boolean lowercaseFirstLetter) {
    return PlatformTestUtil.getTestName(myTestMethodName, lowercaseFirstLetter);
  }
}

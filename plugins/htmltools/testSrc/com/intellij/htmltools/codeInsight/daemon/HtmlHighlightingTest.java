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
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.htmlInspections.*;
import com.intellij.htmltools.HtmlToolsTestsUtil;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlDeprecatedAttributeInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlDeprecatedTagInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlNonExistentInternetResourceInspection;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlPresentationalElementInspection;
import com.intellij.htmltools.lang.annotation.HtmlNonExistentInternetResourcesAnnotator;
import com.intellij.htmltools.xml.util.CheckImageSizeInspection;
import com.intellij.ide.highlighter.HtmlHighlighterFactory;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.HighlightTestInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.psi.XmlPsiBundle;
import com.intellij.xml.util.*;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author Maxim.Mossienko
 */
public class HtmlHighlightingTest extends BasePlatformTestCase {
  @Override
  public String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/htmltools/testData/highlighting";
  }

  private String myOldDoctype;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    myOldDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype(XmlUtil.XHTML_URI, getProject());
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

  @Override
  protected void tearDown() throws Exception {
    try {
      InspectionProfileImpl.INIT_INSPECTIONS = false;
      ExternalResourceManagerEx.getInstanceEx().setDefaultHtmlDoctype(myOldDoctype, getProject());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testemptyAttr() {
    doTest();
  }

  public void testcaseInAttrValue() {
    doTest();
  }

  public void testcaseInTagAndAttr() {
    doTest();
  }

  public void testNoEndTag() {
    doTest();
  }

  public void testNoEndTag2() {
    doTest();
  }

  public void testAboutScheme() {
    doTest();
  }

  public void testIdHtml4() {
    doTest();
  }

  public void testIdHtml4Implicit() {
    doTest();
  }

  public void testIdHtml5() {
    doTest();
  }

  public void testIdHtml5Implicit() {
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    String defaultHtmlDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), getProject());
    try {
      doTest();
    }
    finally {
      manager.setDefaultHtmlDoctype(defaultHtmlDoctype, getProject());
    }
  }

  public void testNoEndTag3() {
    myFixture.configureByFile(getTestName(false) + ".html");
    myFixture.checkHighlighting(true, false, true);
    IntentionAction intention = myFixture.findSingleIntention("Remove extra closing tag");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + "_after.html");
  }

  public void testNoEndTag4() {
    doTest();
  }

  public void testNsDef() {
    doTest();
  }

  public void testExtraEndTag() {
    doTest();
  }

  public void testHtmlWithStrictDtd() {
    doTest();
  }

  public void testHtml5() {
    myFixture.copyDirectoryToProject("html5", "html5");
    myFixture.configureByFile("html5/" + getTestName(false) + "." + "html");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testHtml5_2() {
    doTest();
  }

  public void testXhtml5() {
    myFixture.copyDirectoryToProject("html5", "html5");
    myFixture.configureByFiles("html5/" + getTestName(false) + "." + "xhtml");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testHtml5_3() {
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    String defaultHtmlDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), getProject());
    try {
      myFixture.copyDirectoryToProject("html5", "html5");
      myFixture.configureByFiles("html5/" + getTestName(false) + "." + "html");
      myFixture.checkHighlighting(true, false, false);
    }
    finally {
      manager.setDefaultHtmlDoctype(defaultHtmlDoctype, getProject());
    }
  }

  public void testHtml5_4() {
    myFixture.enableInspections(new HtmlDeprecatedTagInspection());
    doTest();
  }

  public void testSvgAndMathMl() {
    doTest();
  }

  public void testSvgAndMathMlNoNs() {
    doTest();
  }

  public void testFullSvg() {
    doTest();
  }

  public void testRDFa() {
    doTest();
  }

  public void testARIA() {
    doTest();
  }

  public void testXhtml5_2() {
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    String defaultHtmlDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), getProject());
    try {
      myFixture.copyDirectoryToProject("html5",  "html5");
      myFixture.configureByFiles("html5/" + getTestName(false) + "." + "xhtml");
      myFixture.checkHighlighting(true, false, false);
    }
    finally {
      manager.setDefaultHtmlDoctype(defaultHtmlDoctype, getProject());
    }
  }

  public void testHtml5DataAttrs() {
    doTest();
  }

  public void testHtml5DataAttrs1() {
    doTest();
  }

  public void testHtml5DataAttrs2() {
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    String defaultHtmlDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), getProject());
    try {
      doTest();
    }
    finally {
      manager.setDefaultHtmlDoctype(defaultHtmlDoctype, getProject());
    }
  }

  public void testHtml5Equiv() {
    //WEB-5171
    doTest();
  }

  public void testXhtml5DataAttrs() {
    doTest("xhtml");
  }

  public void testXhtml5DataAttrs1() {
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    String defaultHtmlDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), getProject());
    try {
      doTest("xhtml");
    }
    finally {
      manager.setDefaultHtmlDoctype(defaultHtmlDoctype, getProject());
    }
  }

  public void testNoRootHtmlAndHeadAndBody() {
    doTest();
  }

  public void testNoRootHtmlTag() {
    doTest();
  }

  public void testHtmlNoBody() {
    doTest();
  }

  public void testHtmlNoHead() {
    doTest();
  }

  public void testHtmlNoHeadBody() {
    doTest();
  }

  public void testHtml4NoBody() {
    doTest();
  }

  public void testHtml4NoHead() {
    doTest();
  }

  public void testHtml4NoHeadBody() {
    doTest();
  }

  public void testFrameSet() {
    doTest();
  }

  public void testIdValidation() {
    doTest();
  }

  public void testAnchors() {
    doTest();
  }

  public void testBadAnchor() {
    doTest();
  }

  public void testLinks() {
    doTest();
  }

  public void testJSInOnTags() {
    doTest();
  }

  public void testFixedAttributeValues() {
    doTest();
  }

  public void testUnknownHrefTarget() {
    doTest();
  }

  public void testUnknownHrefTarget1() {
    HtmlUnknownTargetInspection inspection = new HtmlUnknownTargetInspection();
    myFixture.disableInspections(inspection);
    doTest();
  }

  public void testTelAndSmsHrefPrefixes() {
    doTest();
  }

  public void testXhtmlMobile() {
    HtmlUnknownTargetInspection inspection = new HtmlUnknownTargetInspection();
    myFixture.disableInspections(inspection);
    doTest();
  }

  public void testWrongRootElementDoNotValidate() {
    myFixture.configureByFile(getTestName(false) + ".html");
    myFixture.checkHighlighting(true, true, false);
  }

  public void testLinksWithSpaces() {
    if (HtmlToolsTestsUtil.isCommunityContext()) return;
    myFixture.configureByFiles("/html file.html", "/Links.html");
    myFixture.checkHighlighting(true, true, false);
  }

  public void testpClosedByBlockElement() {
    doTest();
  }

  public void testWrongRootElement() {
    doTest("xhtml");
  }

  public void testReferencingDirsWithLinkEndingWithSlash() {
    myFixture.configureByFile(getTestName(false) + ".html");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      myFixture.getFile().getContainingDirectory().createSubdirectory("aaa");
    });

    myFixture.checkHighlighting(true, false, false);
  }

  public void testValidationOfSingleTag() {
    doTest();
  }

  public void testDocTypeAndRootTagInUpperCase() {
    doTest();
  }

  public void testOnlyDocType() {
    doTest();
  }

  public void testExternalValidationForXHtml() {
    myFixture.configureByFile(getTestName(false) + ".xhtml");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testAttributeValidationRelaxation() {
    doTest();
  }

  public void testIncompleteDoctype() {
    doTest();
  }

  public void testSOE() {
    doTest();
  }

  public void testSOEFromRelaxedDescriptor() {
    if (HtmlToolsTestsUtil.isCommunityContext()) return;
    doTest("xhtml");
  }

  public void testXIncludeHRef() {
    myFixture.disableInspections(new CheckXmlFileWithXercesValidatorInspection());
    final String testName = getTestName(false);
    final String location = "XInclude.xsd";
    final String url = "http://www.w3.org/2001/XInclude";

    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());

    myFixture.configureByFiles(testName + ".xhtml", location);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testCheckImageSizeInspection() {
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

  public void testCheckValidXmlInScriptBodyInspection() {
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

  public void testDifferentCaseInAttrs() {
    doTest();
  }

  public void testMapIdRequired() {
    doTest();
  }

  public void testUseMap() {
    doTest();
  }

  public void testSelectHasNoWidth() {
    doTest();
  }

  public void testRelativeProtocolUrls() {
    doTest();
  }

  public void testReportAboutEmptyEnd() {
    myFixture.enableInspections(new CheckEmptyTagInspection());
    doTest();
  }

  public void testXHtml11() {
    myFixture.configureByFile(getTestName(false) + ".xhtml");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testEntities() {
    doTest();
    myFixture.configureByFile(getTestName(false) + ".xhtml");
    myFixture.checkHighlighting(true, false, false);
    myFixture.configureByFile(getTestName(false) + "2.xhtml");
    myFixture.checkHighlighting(true, false, false);
  }

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
    assertSame("Escaped el", XmlTokenType.XML_DATA_CHARACTERS, iterator.getTokenType());
    iterator = htmlHighlighter.createIterator(30);
    assertSame("El start", XmlTokenType.XML_DATA_CHARACTERS, iterator.getTokenType());
    iterator = htmlHighlighter.createIterator(35);
    assertSame("El identifier", XmlTokenType.XML_DATA_CHARACTERS, iterator.getTokenType());

    iterator = htmlHighlighter.createIterator(37);
    assertSame("El dot", XmlTokenType.XML_DATA_CHARACTERS, iterator.getTokenType());
    iterator = htmlHighlighter.createIterator(41);
    assertSame("El end", XmlTokenType.XML_DATA_CHARACTERS, iterator.getTokenType());

    iterator = htmlHighlighter.createIterator(57);
    assertSame("El start", XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, iterator.getTokenType());
    iterator = htmlHighlighter.createIterator(61);
    assertSame("El lbracket", XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, iterator.getTokenType());
    iterator = htmlHighlighter.createIterator(63);
    assertSame("El string literal", XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, iterator.getTokenType());

    iterator = htmlHighlighter.createIterator(67);
    assertSame("El end", XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, iterator.getTokenType());
    iterator = htmlHighlighter.createIterator(68);
    assertSame("Attribute value end", XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER, iterator.getTokenType());

    //                10        20        30
    //      0123456789012345678901234567890123456
    text = "<!--- bla --->";
    htmlHighlighter.setText(text);

    iterator = htmlHighlighter.createIterator(6);
    assertSame("Comment content", XmlTokenType.XML_COMMENT_CHARACTERS, iterator.getTokenType());
    iterator = htmlHighlighter.createIterator(12);
    assertSame("Comment end", XmlTokenType.XML_COMMENT_END, iterator.getTokenType());

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
      "char entity in comment",
      XmlTokenType.XML_COMMENT_CHARACTERS,
      iterator.getTokenType()
    );

    iterator = htmlHighlighter.createIterator(12);
    assertEquals(
      "entity ref in comment",
      XmlTokenType.XML_COMMENT_CHARACTERS,
      iterator.getTokenType()
    );

    //                10
    //      01234567890123456789
    text = "<div> < in </div>";
    htmlHighlighter.setText(text);
    iterator = htmlHighlighter.createIterator(6);
    assertEquals(
      "< before space should be tag data",
      XmlTokenType.XML_DATA_CHARACTERS,
      iterator.getTokenType()
    );

    iterator = htmlHighlighter.createIterator(8);
    assertEquals(
      "should be tag data",
      XmlTokenType.XML_DATA_CHARACTERS,
      iterator.getTokenType()
    );
  }

  public void testDeprecatedAndPresentationalTags() {
    myFixture.enableInspections(new HtmlDeprecatedTagInspection());
    doTest();
  }

  public void testDeprecatedAttributes() {
    myFixture.enableInspections(new HtmlDeprecatedAttributeInspection());
    doTest();
  }

  public void testDeprecatedAndPresentationalTagsHtml5() {
    myFixture.enableInspections(new HtmlDeprecatedTagInspection());
    doTest();
  }

  public void testMenuHtml5TagNotDeprecated() {
    myFixture.enableInspections(new HtmlDeprecatedTagInspection());
    doTest();
  }

  public void testPresentationalTagInHtml5() {
    doTest();
  }

  public void testPresentationalTagInXhtml5() {
    doTest("xhtml");
  }

  public void testHtmlPsi() {
    myFixture.configureByFile(getTestName(false) + ".html");
    final XmlTag tag = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()), XmlTag.class);
    assertNotNull(tag);
    assertNull(tag.getAttribute("type", ""));
    assertNotNull(tag.getAttribute("type", "http://tapestry.apache.org/schema/tapestry_5_0_0.xsd"));
    assertNotNull(tag.getAttribute("t:type", ""));
    assertNotNull(tag.getAttribute("type", "http://tapestry.apache.org/schema/tapestry_5_0_0.xsd"));
  }

  public void testParametrizedReference() {
    myFixture.configureByFiles(getTestName(false) + ".html",
                               getTestName(false) + ".css");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testAddCustomTagFixTest() {
    doTestWithAddToCustomsQuickFix("Add zzz to custom html tags");
  }

  public void testAddCustomTagFixTest2() {
    doTestWithAddToCustomsQuickFix("Add abba to custom html tags", 2);
  }

  public void testAddCustomAttributeFixTest() {
    doTestWithAddToCustomsQuickFix("Add zzz to custom html attributes");
  }

  public void testAddCustomBooleanAttribute() {
    doTestWithAddToCustomsQuickFix(XmlAnalysisBundle.message("html.quickfix.add.custom.html.boolean.attribute", "title"));
  }

  public void testAddNotRequiredAttributeFixTest() {
    doTestWithAddToCustomsQuickFix("Add alt to not required html attributes");
  }

  public void testUnknownBooleanAttributeWithAddAttributeValueQuickFix() {
    myFixture.configureByFile(getTestName(false) + ".html");
    myFixture.checkHighlighting(true, false, true);
    IntentionAction intention = myFixture.findSingleIntention(XmlPsiBundle.message("xml.quickfix.add.attribute.value.text"));
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + "_after.html");
  }

  public void testDataUri() {
    doTest();
  }

  public void testHtml5QuickFixShouldBeFirst() {
    doTestHtml5QuickFixShouldBeFirst();
  }

  public void testHtml5QuickFixShouldBeFirst1() {
    doTestHtml5QuickFixShouldBeFirst();
  }

  public void testPlaceChecking() {
    doTest();
  }

  public void testHtml5Entities() {
    myFixture.enableInspections(new CheckDtdReferencesInspection());
    doTest();
  }

  public void testHtml5EmptyAttr() {
    doTest();
  }

  public void testHtml5Autocomplete() {
    doTest();
  }

  public void testHtml5EntitiesWithoutRootTag() {
    // restore HTML5 doctype, because we're unable to determine it without doctype
    ExternalResourceManagerEx.getInstanceEx().setDefaultHtmlDoctype(myOldDoctype, getProject());
    myFixture.enableInspections(new CheckDtdReferencesInspection());
    doTest();
  }

  public void testWebLinksValidator1() throws Exception {
    doTestWebLinks(false);
  }

  public void testWebLinksValidator2() throws Exception {
    try (ServerSocket socket = new ServerSocket(1118)) {
      doTestWebLinks(false);
    }
  }

  public void testWebLinksValidator3() throws Exception {
    doTestWebLinks(true);
  }

  public void testWebLinksValidator4() throws Exception {
    doTestWebLinks(true);
  }

  public void testWebLinksValidator5() throws Exception {
    doTestWebLinks(true);
  }

  public void testWebLinksValidator6() throws Exception {
    doTestWebLinks(true);
  }

  public void testWebLinksValidator7() throws Exception {
    doTestWebLinks(true);
  }

  public void testWI5823() {
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    String defaultHtmlDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), getProject());
    try {
      doTest();
    }
    finally {
      manager.setDefaultHtmlDoctype(defaultHtmlDoctype, getProject());
    }
  }

  public void testWEB5171() {
    myFixture.enableInspections(new HtmlUnknownAttributeInspection());
    doTest();
  }

  public void testWEB7422() {
    myFixture.enableInspections(new HtmlUnknownAttributeInspection());
    doTest();
  }

  public void testWEB404() {
    doTest();
  }

  public void testWEB8397() {
    myFixture.enableInspections(new HtmlUnknownTagInspection());
    doTest();
  }

  public void testHtml5NonW3C() {
    // if you see this test fail, please make sure that all attributes/elements containing nonW3C
    // in the declaration were removed from the schema
    // please update this test with all attributes/elements you remove this way
    myFixture.enableInspections(new HtmlDeprecatedTagInspection());
    doTest();
  }

  public void testImgLoading() {
    doTest();
  }

  public void testSpelling() {
    myFixture.enableInspections(new SpellCheckingInspection());
    doTest();
  }

  public void testBase() {
    myFixture.copyFileToProject("CheckImageSizeInspection.gif", "images/CheckImageSizeInspection.gif");
    myFixture.configureByFile("/Base.html");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testBaseUrl() {
    doTest();
  }

  public void testCustomTagHighlighting() {
    HtmlUnknownTagInspection inspection = new HtmlUnknownTagInspection();
    inspection.updateAdditionalEntries("custom-tag,custom2-TAG", getTestRootDisposable());
    myFixture.enableInspections(inspection);

    HighlightTestInfo info = myFixture.testFile(getTestName(false) + ".html");
    info.checkSymbolNames();
    info.test();
  }

  private void doTestWebLinks(boolean startTestingLocalServer) throws Exception {
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
    myFixture.configureByFile(getTestName(false) + ".html");
    myFixture.doHighlighting();
    ShowIntentionsPass.IntentionsInfo intentions = ShowIntentionsPass.getActionsToShow(myFixture.getEditor(), myFixture.getFile());
    assertFalse(intentions.isEmpty());
    CachedIntentions actions = CachedIntentions.createAndUpdateActions(getProject(), myFixture.getFile(), myFixture.getEditor(), intentions);
    assertNotEmpty(actions.getAllActions());
    IntentionAction firstAction = actions.getAllActions().get(0).getAction();
    assertEquals(new SwitchToHtml5WithHighPriorityAction().getText(), firstAction.getText());
  }

  private void doTestWithAddToCustomsQuickFix(String intentionName) {
    doTestWithAddToCustomsQuickFix(intentionName, 1);
  }

  private void doTestWithAddToCustomsQuickFix(String intentionName, final int fixesNum) {
    final String s = getTestName(false);
    myFixture.configureByFile(s + ".html");
    Collection<HighlightInfo> highlightInfos = myFixture.doHighlighting();
    assertEquals(fixesNum, highlightInfos.size());
    IntentionAction intention = myFixture.findSingleIntention(intentionName);
    myFixture.launchAction(intention);
    assertEquals(0, myFixture.doHighlighting().stream().filter(el -> el.type != HighlightInfoType.INFORMATION).count());
  }

  protected void doTest(String ext) {
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
}

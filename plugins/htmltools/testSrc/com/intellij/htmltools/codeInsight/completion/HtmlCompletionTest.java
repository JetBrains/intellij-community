package com.intellij.htmltools.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.htmlInspections.*;
import com.intellij.htmltools.HtmlToolsTestsUtil;
import com.intellij.ide.DataManager;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.util.Trinity;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.Consumer;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings({"ALL"})
public class HtmlCompletionTest extends BasePlatformTestCase {
  private String myOldDoctype;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    myOldDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype(XmlUtil.XHTML_URI, getProject());
    InspectionProfileImpl.INIT_INSPECTIONS = true;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      InspectionProfileImpl.INIT_INSPECTIONS = false;
      ExternalResourceManagerEx externalResourceManager =
        (ExternalResourceManagerEx)ApplicationManager.getApplication().getServiceIfCreated(ExternalResourceManager.class);
      if (externalResourceManager != null) {
        externalResourceManager.setDefaultHtmlDoctype(myOldDoctype, getProject());
      }
    }
    finally {
      super.tearDown();
    }
  }

  protected String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/htmltools/testData/completion/";
  }

  public void testXHtmlCompletion() throws Exception {
    CamelHumpMatcher.forceStartMatching(myFixture.getTestRootDisposable());
    configureByFile("/1.xhtml");
    checkResultByFile("/1_after.xhtml");

    configureByFile("/Entity.xhtml");
    checkResultByFile("/Entity_after.xhtml");

    configureByFile("/Entity2.xhtml");
    checkResultByFile("/Entity2_after.xhtml");

    configureByFile("/Entity3.xhtml");
    checkResultByFile("/Entity3_after.xhtml");

    configureByFile("/EntityInAttr.xhtml");
    checkResultByFile("/EntityInAttr_after.xhtml");
  }

  private void checkResultByFile(String path) throws Exception {
    myFixture.checkResultByFile(path);
  }

  private void configureByFile(String path) throws Exception {
    myFixture.configureByFile(path);
    myFixture.completeBasic();
  }

  public void testXhtmlCompletionInHtml() throws Exception {
    configureByFile("/xhtmlinhtml.html");
    checkResultByFile("/xhtmlinhtml_after.html");
  }

  public void testXhtmlCompletion() throws Exception {
    configureByFile("/xhtml.xhtml");
    checkResultByFile("/xhtml_after.xhtml");
  }

  public void testHtmlCompletion() throws Exception {
    CamelHumpMatcher.forceStartMatching(myFixture.getTestRootDisposable());

    configureByFile("/1.html");
    checkResultByFile("/1_after.html");

    configureByFile("/2.html");
    checkResultByFile("/2_after.html");

    configureByFile("/3.html");
    checkResultByFile("/3_after.html");

    configureByFile("/5.html");
    checkResultByFile("/5_after.html");

    configureByFile("/6.html");
    checkResultByFile("/6_after.html");

    configureByFile("/7.html");
    checkResultByFile("/7_after.html");

    configureByFile("/8.html");
    checkResultByFile("/8_after.html");

    myFixture.copyFileToProject("/9.png");
    configureByFile("/9.html");
    checkResultByFile("/9_after.html");

    if (!HtmlToolsTestsUtil.isCommunityContext()) {
      configureByFile("/10.html");
      assertContainsElements(myFixture.getLookupElementStrings(), "text/javascript");
    }

    configureByFile("/12.html");
    checkResultByFile("/12_after.html");

    configureByFile("/12_2.html");
    checkResultByFile("/12_2_after.html");

    configureByFile("/13.html");
    checkResultByFile("/13_after.html");

    configureByFile("/14.html");
    checkResultByFile("/14_after.html");

    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), getProject());
    configureByFile("/14a.html");
    myFixture.completeBasic();
    assertEquals(Arrays.asList("application/x-www-form-urlencoded", "multipart/form-data", "text/plain"), myFixture.getLookupElementStrings());
    manager.setDefaultHtmlDoctype(XmlUtil.XHTML_URI, getProject());

    configureByFile("/Link.Rel.html");
    checkResultByFile("/Link.Rel_after.html");

    configureByFile("/Link.Type.html");
    myFixture.type('\n');
    checkResultByFile("/Link.Type_after.html");

    configureByFile("/Link.Media.html");
    checkResultByFile("/Link.Media_after.html");

    configureByFile("/Language.Script.html");
    myFixture.type('\n');
    checkResultByFile("/Language.Script_after.html");

    if (!HtmlToolsTestsUtil.isCommunityContext()) {
      configureByFile("/Type.Script2.html");
      assertContainsElements(myFixture.getLookupElementStrings(), "text/javascript");
    }

    configureByFile("/15.html");
    checkResultByFile("/15_after.html");

    configureByFile("/Entity.html");
    checkResultByFile("/Entity_after.html");

    configureByFile("/Meta.html");
    checkResultByFile("/Meta_after.html");

    configureByFile("/EntityHtml5.html");
    checkResultByFile("/EntityHtml5_after.html");
  }

  public void testHtmlImageWidthCompletionFromDataUri() {
    myFixture.testCompletion(getTestName(true) + ".html", getTestName(true) + "_after.html");
  }

  public void testHtmlImageHeightCompletionFromDataUri() {
    myFixture.testCompletion(getTestName(true) + ".html", getTestName(true) + "_after.html");
  }

  public void testHtmlCompletion4() throws Exception {
    configureByFile("/4.html");
    myFixture.type('\n');
    checkResultByFile("/4_after.html");
 }

  public void testCharsetsCompletion() throws Exception {
    configureByFile("/charset.html");
    myFixture.type('\n');
    checkResultByFile("/charset_after.html");

    configureByFile("/charset2.html");
    checkResultByFile("/charset2_after.html");
  }

  public void testMimeTypesCompletion() throws Exception {
    configureByFile("/mimetypes.html");
    checkResultByFile("/mimetypes_after.html");
  }

  public void testAlink() throws Throwable {
    configureByFile("/11.html");
    checkResultByFile("/11_after.html");
  }

  public void testLinkWithSpace() throws Exception {
    configureByFile("/htmlFileWith Space.html");
    checkResultByFile("/htmlFileWith Space_after.html");
  }

  public void testColonFinishingLookup() throws Throwable {
    configureByFile(getTestName(false) + ".xhtml");
    myFixture.type(':');
    checkResultByFile(getTestName(false) + "_after.xhtml");
    myFixture.assertPreferredCompletionItems(0, "h:commandLink", "h:outputText");
  }

  public void testGtFinishingEndOfFile() throws Throwable {
    configureByFile(getTestName(false) + ".html");
    myFixture.type('c');
    assertOrderedEquals(myFixture.getLookupElementStrings(), HtmlUtil.SCRIPT_TAG_NAME, "noscript");
    myFixture.type('>');
    checkResultByFile(getTestName(false) + "_after.html");
  }

  public void testDotTypeWhileActiveLookupInFileReference() throws Throwable {
    final String path = getTestName(false) + ".html";
    configureByFile(path);
    Assert.assertNotNull(getActiveLookup());
    myFixture.type('.');
    myFixture.testCompletionVariants(path, path);
  }

  private LookupImpl getActiveLookup() {
    return (LookupImpl)LookupManager.getActiveLookup(myFixture.getEditor());
  }

  public void testCompletionInLabelFor() throws Exception {
    final String testName = getTestName(false);
    configureByFile("/" + testName +".html");
    checkResultByFile("/" + testName + "_after.html");

    configureByFile("/" + testName +"2.html");
    checkResultByFile("/" + testName + "2_after.html");

    configureByFile("/" + testName +"3.html");
    checkResultByFile("/" + testName + "3_after.html");
  }

  public void testAriaLabelledBy() throws Exception {
    final String testName = getTestName(false);
    configureByFile("/" + testName +".html");
    checkResultByFile("/" + testName + "_after.html");
  }

  public void testIdCompletion() throws Exception {
    final String testName = getTestName(true);
    configureByFile("/" + testName +".html");
    checkResultByFile("/" + testName + "_after.html");
  }

  public void testNotRequiredAttributeIsNotInsertedByCompletion() throws Exception {
    doTestWithHtmlInspectionEnabled(
      RequiredAttributesInspection.class,
      "alt"
    );
  }

  public void testAttributesTemplateCancelWithSpace() throws Exception {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    configureByFile(getTestName(false) + ".html");
    type('b');
    type('r');
    type(' ');
    EditorActionHandler actionHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ESCAPE);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
                                               actionHandler.execute(myFixture.getEditor(), null,
                                                                     DataManager.getInstance().getDataContext());
                                               actionHandler.execute(myFixture.getEditor(), null,
                                                                     DataManager.getInstance().getDataContext());
                                             });
    checkResultByFile(getTestName(false) + "_after.html");
  }

  public void testAttributesTemplateCancelWithSpace1() throws Exception {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    configureByFile(getTestName(false) + ".html");
    Runnable r = new Runnable() {
      @Override
      public void run() {
        type('b');
        type('r');
        type(' ');
//          ((LookupImpl)LookupManager.getActiveLookup(myFixture.getEditor())).hide();
        CaretModel caretModel = myFixture.getEditor().getCaretModel();
        caretModel.moveToOffset(caretModel.getOffset() + 1);
        type(' ');
      }
    };
    ApplicationManager.getApplication().invokeAndWait(r);
    checkResultByFile(getTestName(false) + "_after.html");
  }

  private void type(char c) {
    myFixture.type(c);
  }

  public void testTagClosing() throws Exception {
    configureByFile(getTestName(false) + ".html");
    checkResultByFile(getTestName(false) + "_after.html");

    configureByFile(getTestName(false) + "_xhtml.html");
    checkResultByFile(getTestName(false) + "_xhtml_after.html");
  }

  public void testAdditionalHtmlTagsInsertedByCompletion() throws Exception {
    final HtmlUnknownTagInspection inspection = new HtmlUnknownTagInspection();

    inspection.updateAdditionalEntries("zZz", getTestRootDisposable());

    doTestWithHtmlInspectionEnabled("", inspection);
    doTestWithHtmlInspectionEnabled("2", inspection);
  }

  public void testAdditionalHtmlAttributesInsertedByCompletion() throws Exception {
    final HtmlUnknownAttributeInspection inspection = new HtmlUnknownAttributeInspection();

    inspection.updateAdditionalEntries("zZz", getTestRootDisposable());

    doTestWithHtmlInspectionEnabled("2", inspection);
    doTestWithHtmlInspectionEnabled("", inspection);
  }

  private void doTestWithHtmlInspectionEnabled(Class inspectionClass, String value) throws Exception {
    doTestWithHtmlInspectionEnabled("", inspectionClass, value);
  }

  private void doTestWithHtmlInspectionEnabled(String ext, LocalInspectionTool configuredInspection) throws Exception {
    final String testName = getTestName(false);
    myFixture.configureByFile("/" + testName + ext + ".html");
    myFixture.enableInspections(configuredInspection);
    myFixture.completeBasic();
    checkResultByFile("/" + testName + ext + "_after.html");
  }

  private void doTestWithHtmlInspectionEnabled(String ext, Class inspectionClass, String value) throws Exception {
    XmlEntitiesInspection tool = (XmlEntitiesInspection) inspectionClass.newInstance();
    tool.addEntry(value);

    doTestWithHtmlInspectionEnabled(ext, (LocalInspectionTool)tool);
  }

  public void testEncodeUrl() throws Exception {
    myFixture.configureByFile(getTestName(false) + ".html");
    myFixture.copyFileToProject(getTestName(false) + ".png", "i/1717_image.png");
    final LookupElement[] items = myFixture.complete(CompletionType.BASIC, 2);

    assertEquals(1, items.length);
    assertEquals("1717_image.png (16x16)", items[0].toString());
    assertInstanceOf(items[0].getObject(), PsiElement.class);

    myFixture.type('\t');
    checkResultByFile(getTestName(false) + "_after.html");
  }

  public void testPathCompletion1() throws Exception {
    myFixture.configureByFile(getTestName(false) + ".html");
    myFixture.copyFileToProject("EncodeUrl.png", "images/1717_image.png");
    myFixture.copyFileToProject("9.png", "images/numbers/17.png");

    final LookupElement[] items = myFixture.complete(CompletionType.BASIC, 2);

    assertEquals(2, items.length);
    assertEquals("17.png (16x16)", items[0].toString());
    assertEquals("1717_image.png (16x16)", items[1].toString());

    final LookupElement element = items[1];
    assertInstanceOf(element.getObject(), PsiElement.class);

    getActiveLookup().setCurrentItem(element);
    getActiveLookup().finishLookup(Lookup.NORMAL_SELECT_CHAR);
    checkResultByFile(getTestName(false) + "_after.html");
  }

  public void testPathCompletion2() throws Exception {
    myFixture.configureByFile(getTestName(false) + ".html");
    myFixture.copyFileToProject("EncodeUrl.png", "images/1717_image.png");
    myFixture.copyFileToProject("9.png", "images/numbers/17.png");
    myFixture.copyFileToProject("PathCompletion2.html", "images/numbers/not_image.html");

    final LookupElement[] items = myFixture.complete(CompletionType.BASIC, 2);

    assertEquals(1, items.length);
    assertEquals("17.png (16x16)", items[0].toString());

    final LookupElement element = items[0];
    assertInstanceOf(element.getObject(), PsiElement.class);

    getActiveLookup().setCurrentItem(element);
    getActiveLookup().finishLookup(Lookup.NORMAL_SELECT_CHAR);
    checkResultByFile(getTestName(false) + "_after.html");
  }

  public void testHtmlWordCompletion() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".html", getTestName(false) + "_after.html");
  }

  public void testHtml5_1() throws Exception {
    configureByFile("/html5_1.html");
    myFixture.type('\n');
    checkResultByFile("/html5_1_after.html");
  }

  public void testHtml5_2() throws Exception {
    configureByFile("/html5_2.html");
    checkResultByFile("/html5_2_after.html");
  }

  public void testHtml5_3() throws Exception {
    configureByFile("/html5_3.html");
    checkResultByFile("/html5_3_after.html");
  }

  public void testHtml5_4() throws Exception {
    configureByFile("/html5_4.html");
    checkResultByFile("/html5_4_after.html");
  }

  public void testHtml5_5() throws Exception {
    configureByFile("/html5_5.html");
    checkResultByFile("/html5_5_after.html");
  }

  public void testHtml5_6() throws Exception {
    configureByFile("/html5_6.html");
    checkResultByFile("/html5_6_after.html");
  }

  public void testHtml5_7() throws Exception {
    configureByFile("/html5_7.html");
    checkResultByFile("/html5_7_after.html");
  }

  public void testHtml5_8() throws Exception {
    configureByFile("/html5_8.html");
    checkResultByFile("/html5_8_after.html");
  }

  public void testHtml5_9() throws Exception {
    configureByFile("/html5_9.html");
    checkResultByFile("/html5_9_after.html");
  }

  public void testHtml5_10() throws Exception {
    configureByFile("/html5_10.html");
    checkResultByFile("/html5_10_after.html");
  }

  public void testHtml5_11() throws Exception {
    myFixture.testCompletionTyping("/html5_11.html", "\n", "/html5_11_after.html");
  }

  public void testHtml5_12() throws Exception {
    configureByFile("/html5_12.html");
    checkResultByFile("/html5_12_after.html");
  }

  public void testHtml5_13() throws Exception {
    myFixture.testCompletionTyping("/html5_13.html", "\n", "/html5_13_after.html");
  }

  public void testHtml5_14() throws Exception {
    configureByFile("/html5_14.html");
    checkResultByFile("/html5_14_after.html");
  }

  public void testHtml5_15() throws Exception {
    myFixture.testCompletionTyping("/html5_15.html", "\n", "/html5_15_after.html");
  }

  public void testHtml5_16() throws Exception {
    configureByFile("/html5_16.html");
    myFixture.type('\n');
    checkResultByFile("/html5_16_after.html");
  }

  public void testHtml5DataAttrs() throws Exception {
    configureByFile("/html5DataAttrs.html");
    checkResultByFile("/html5DataAttrs_after.html");
  }

  public void testHtml5Duplicates() throws Exception {
    checkDuplicates(myFixture.getCompletionVariants("/html5Duplicates.html"), "a");
  }

  public void testScriptTypeDuplicates() throws Exception {
    checkDuplicates(myFixture.getCompletionVariants("/scriptTypeDuplicates.html"), "module");
  }

  protected void checkDuplicates(@Unmodifiable List<String> variants, String... tags) {
    variants = new ArrayList<>(variants);
    for (String tag : tags) {
      assertTrue("Not in list " + tag, variants.remove(tag));
      assertFalse("Duplicate " + tag, variants.contains(tag));
    }
  }

  public void testHtmlNsDuplicates() throws Exception {
    final List<String> variants = myFixture.getCompletionVariants("/htmlNsDuplicates.html");
    checkDuplicates(variants, "http://www.w3.org/1999/xhtml");
  }

  public void testHtml5DataAttrs1() throws Exception {
    configureByFile("/html5DataAttrs1.html");
    checkResultByFile("/html5DataAttrs1_after.html");
  }

  public void testHtml5DataAttrs2() throws Exception {
    configureByFile("/html5DataAttrs2.html");
    checkResultByFile("/html5DataAttrs2_after.html");
  }

  public void testFigcaptionTag1() throws Exception {
    configureByFile("/figcaptionTag1.html");
    checkResultByFile("/figcaptionTag1_after.html");
  }

  public void testFigcaptionTag2() throws Exception {
    configureByFile("/figcaptionTag2.html");
    checkResultByFile("/figcaptionTag2_after.html");
  }

  public void testHtml5DataAttrs3() throws Exception {
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    String defaultHtmlDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), getProject());
    try {
      configureByFile("/html5DataAttrs3.html");
      checkResultByFile("/html5DataAttrs3_after.html");
    }
    finally {
      manager.setDefaultHtmlDoctype(defaultHtmlDoctype, getProject());
    }
  }

  public void testXhtml5DataAttrs() throws Exception {
    configureByFile("/xhtml5DataAttrs.xhtml");
    checkResultByFile("/xhtml5DataAttrs_after.xhtml");
  }

  public void testXhtml5DataAttrs1() throws Exception {
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    String defaultHtmlDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), getProject());
    try {
      configureByFile("/xhtml5DataAttrs1.xhtml");
      checkResultByFile("/xhtml5DataAttrs1_after.xhtml");
    }
    finally {
      manager.setDefaultHtmlDoctype(defaultHtmlDoctype, getProject());
    }
  }

  public void testHtml5AttributesOrder() throws Exception {
    myFixture.configureByFile(getTestName(true) + ".html");

    final LookupElement[] items = myFixture.complete(CompletionType.BASIC);
    assertTrue(items.length > 3);
    assertEquals("media", items[0].getLookupString());
    assertEquals("type", items[1].getLookupString());
    assertEquals("about", items[2].getLookupString());
  }

  public void testAnchorReferenceCompletion_WI4407() throws Exception {
    configureByFile("/anchorReferenceCompletion1.html");
    checkResultByFile("/anchorReferenceCompletion1_after.html");

    configureByFile("/anchorReferenceCompletion2.html");
    checkResultByFile("/anchorReferenceCompletion2_after.html");
  }

  public void testSrcValue1() throws Exception {
    doTest();
  }

  public void testSrcValue2() throws Exception {
    doTest();
  }

  public void testSrcValue3() throws Exception {
    doTest();
  }

  public void testSrcValue4() throws Exception {
    doTest();
  }

  public void testSrcValue5() throws Exception {
    String fileName = getTestName(true);
    myFixture.addFileToProject("foo/bar/jquery.js", "");
    myFixture.configureByFile("/" + fileName + ".html");
    myFixture.complete(CompletionType.BASIC, 2);
    checkResultByFile("/" + fileName + "_after.html");
  }

  public void testSrcValue6() throws Exception {
    String fileName = getTestName(true);
    myFixture.addFileToProject("myImages/jquery.jpg", "");
    myFixture.addFileToProject("myImages2/jquery.jpg", "");
    myFixture.configureByFile("/" + fileName + ".html");
    myFixture.completeBasic();
    myFixture.type('\n');
    checkResultByFile("/" + fileName + "_after.html");
  }

  public void testSrcSet1() throws Exception {
    String fileName = getTestName(true);
    myFixture.addFileToProject("myImages/jquery.jpg", "");
    myFixture.addFileToProject("myImages2/jquery.jpg", "");
    myFixture.configureByFile("/" + fileName + ".html");
    myFixture.completeBasic();
    myFixture.type('\n');
    checkResultByFile("/" + fileName + "_after.html");
  }

  public void testSrcSet2() throws Exception {
    String fileName = getTestName(true);
    myFixture.addFileToProject("myImages/jquery.jpg", "");
    myFixture.addFileToProject("myImages2/jquery.jpg", "");
    myFixture.configureByFile("/" + fileName + ".html");
    myFixture.completeBasic();
    myFixture.type('\n');
    checkResultByFile("/" + fileName + "_after.html");
  }

  public void testSrcSet3() throws Exception {
    String fileName = getTestName(true);
    myFixture.addFileToProject("myImages/jquery.jpg", "");
    myFixture.addFileToProject("myImages2/jquery.jpg", "");
    myFixture.configureByFile("/" + fileName + ".html");
    myFixture.completeBasic();
    myFixture.type('\n');
    checkResultByFile("/" + fileName + "_after.html");
  }

  public void testSrcSet4() throws Exception {
    String fileName = getTestName(true);
    myFixture.addFileToProject("myImages/jquery.jpg", "");
    myFixture.addFileToProject("myImages2/jquery.jpg", "");
    myFixture.configureByFile("/" + fileName + ".html");
    myFixture.completeBasic();
    myFixture.type('\n');
    checkResultByFile("/" + fileName + "_after.html");
  }

  public void testHtmlAttributeValueNoTagEnd() throws Exception {
    myFixture.addFileToProject("myImages/jquery.jpg", "");
    myFixture.addFileToProject("myImages2/jquery.jpg", "");
    myFixture.configureByFile(getTestName(true) + ".html");
    myFixture.completeBasic();
    myFixture.type('\t');
    checkResultByFile(getTestName(true) + "_after.html");
  }

  public void testObjectDataValue() throws Exception {
    doTest();
  }

  public void testVideoPosterValue() throws Exception {
    doTest();
  }

  public void testWI5823() throws Exception {
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    String defaultHtmlDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), getProject());
    try {
      final List<String> variants = myFixture.getCompletionVariants("/" + getTestName(true) + ".html");
      assertTrue(variants.contains("li"));
    }
    finally {
      manager.setDefaultHtmlDoctype(defaultHtmlDoctype, getProject());
    }
  }

  public void testCustomTagCompletion() throws Exception {
    myFixture.enableInspections(HtmlUnknownTagInspection.class);

    final String customTag = "mycustomtag";
    myFixture.configureByFile("/customTagCompletion.html");
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(getProject()).getCurrentProfile();
    profile.modifyToolSettings(HtmlUnknownTagInspection.TAG_KEY, myFixture.getFile(), new Consumer<HtmlUnknownElementInspection>() {
      @Override
      public void consume(HtmlUnknownElementInspection inspection) {
        inspection.addEntry(customTag);
      }
    });
    myFixture.completeBasic();
    assertTrue(myFixture.getLookupElementStrings().contains(customTag));
  }

  public void testAttributeValueQuotes() throws Exception {
    myFixture.configureByFile("/attributeValueQuotes.html");
    myFixture.type('=');
    myFixture.checkResultByFile("/attributeValueQuotes_after.html");
  }

  public void testAttributeValueSingleQuotes() throws Exception {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    final CodeStyleSettings.QuoteStyle quote = settings.HTML_QUOTE_STYLE;
    try {
      settings.HTML_QUOTE_STYLE = CodeStyleSettings.QuoteStyle.Single;
      myFixture.configureByFile("/attributeValueQuotes.html");
      myFixture.type('=');
      myFixture.checkResultByFile("/attributeValueQuotes_single_after.html");
    } finally {
      settings.HTML_QUOTE_STYLE = quote;
    }
  }

  public void testAttributeValueNoneQuotes() throws Exception {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    final CodeStyleSettings.QuoteStyle quote = settings.HTML_QUOTE_STYLE;
    try {
      settings.HTML_QUOTE_STYLE = CodeStyleSettings.QuoteStyle.None;
      myFixture.configureByFile("/attributeValueQuotes.html");
      myFixture.type('=');
      myFixture.checkResultByFile("/attributeValueQuotes_none_after.html");
    } finally {
      settings.HTML_QUOTE_STYLE = quote;
    }
  }

  public void testDoNotCompleteEmptyAttributeValues() {
    myFixture.testCompletionVariants("/doNotCompleteEmptyAttributeValues.html", "checked");
  }

  public void testNoBodyElementsInHead() {
    myFixture.configureByFile(getTestName(true) + ".html");
    myFixture.completeBasic();
    assertContainsElements(myFixture.getLookupElementStrings(), "title", "meta");
    assertDoesntContain(myFixture.getLookupElementStrings(), "div", "h1");
  }

  public void testWeb7228() throws Exception {
    doTest();
  }

  public void testWeb2359() throws Exception {
    final List<String> variants = myFixture.getCompletionVariants("/" + getTestName(true) + ".html");
    assertContainsElements(variants, "p");
  }

  public void testAttributesStayOnSecondCompletion() {
    myFixture.configureByText("foo.html", "<div onmouse<caret>");
    final String[] expected = {"onmousemove", "onmouseup", "onmouseout", "onmousedown", "onmouseover"};
    myFixture.complete(CompletionType.BASIC, 0);
    assertSameElements(myFixture.getLookupElementStrings(), expected);
    myFixture.complete(CompletionType.BASIC, 1);
    assertSameElements(myFixture.getLookupElementStrings(), expected);
    myFixture.complete(CompletionType.BASIC, 2);
    assertSameElements(myFixture.getLookupElementStrings(), expected);
    myFixture.complete(CompletionType.BASIC, 3);
    assertSameElements(myFixture.getLookupElementStrings(), expected);
  }

  public void testTagTailCompletion() {
    for (Trinity<String, String, String> test : List.of(Trinity.create("<map>|", "area", "|>"),
                                                        Trinity.create("<head>|", "base", "|>"),
                                                        Trinity.create("<p>foo|", "br", ">|"),
                                                        Trinity.create("<table><colgroup>|", "col", "|>"),
                                                        Trinity.create("|", "embed", "|>"),
                                                        Trinity.create("<frameset>|", "frame", ">|"),
                                                        Trinity.create("<p>foo|", "hr", ">|"),
                                                        Trinity.create("|", "img", " src=\"\"|>"),
                                                        Trinity.create("|", "input", "|>"),
                                                        Trinity.create("<head>|", "link", "|>"),
                                                        Trinity.create("<head>|", "meta", "|>"),
                                                        Trinity.create("<object>|", "param", " name=\"\" value=\"\"|>"),
                                                        Trinity.create("<audio>|", "source", "|>"),
                                                        Trinity.create("<video>|", "track", " src=\"\"|>"),
                                                        Trinity.create("foo|bar", "wbr", ">|"))) {
      try {
        myFixture.configureByText("test.html", "<!doctype html>\n" + test.first.replace("|", "<caret>"));
        myFixture.type("<");
        myFixture.completeBasic();
        myFixture.type(test.second + "\n");
        myFixture.checkResult("<!doctype html>\n" + test.first.replace("|", "<" + test.second + test.third.replace("|", "<caret>")));
      }
      catch (AssertionError error) {
        throw (AssertionError)new AssertionError("Failed for " + test.second).initCause(error);
      }
    }
  }

  private void doTest() throws Exception {
    configureByFile("/" + getTestName(true) + ".html");
    checkResultByFile("/" + getTestName(true) + "_after.html");
  }

  @NotNull
  private HtmlCodeStyleSettings getHtmlSettings() {
    return CodeStyle.getSettings(myFixture.getProject())
                    .getCustomSettings(HtmlCodeStyleSettings.class);
  }
}

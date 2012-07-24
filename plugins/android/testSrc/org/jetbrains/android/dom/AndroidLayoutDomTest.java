package org.jetbrains.android.dom;

import com.android.sdklib.SdkConstants;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.android.inspections.CreateFileResourceQuickFix;
import org.jetbrains.android.inspections.CreateValueResourceQuickFix;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author coyote
 */
public class AndroidLayoutDomTest extends AndroidDomTest {
  public AndroidLayoutDomTest() {
    super(false, "dom/layout");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    CamelHumpMatcher.forceStartMatching(getTestRootDisposable());
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);

    // copy mock fragment, because it is not included to old android.jar
    // todo: create normal mock Android sdk
    copyFileToProject("Fragment.java", "src/android/app/Fragment.java");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/layout/" + testFileName;
  }

  public void testAttributeNameCompletion1() throws Throwable {
    doTestCompletionVariants("an1.xml", "layout_weight", "layout_width");
  }

  public void testAttributeNameCompletion2() throws Throwable {
    toTestCompletion("an2.xml", "an2_after.xml");
  }

  public void testAttributeNameCompletion3() throws Throwable {
    toTestCompletion("an3.xml", "an3_after.xml");
  }

  public void testAttributeNameCompletion4() throws Throwable {
    toTestCompletion("an4.xml", "an4_after.xml");
  }

  public void testAttributeNameCompletion5() throws Throwable {
    toTestCompletion("an5.xml", "an5_after.xml");
  }

  public void testCommonPrefixIdea63531() throws Throwable {
    toTestCompletion("commonPrefixIdea63531.xml", "commonPrefixIdea63531_after.xml");
  }

  public void testHighlighting() throws Throwable {
    doTestHighlighting("hl.xml");
  }

  public void testHighlighting2() throws Throwable {
    copyFileToProject("integers.xml", "res/values/integers.xml");
    doTestHighlighting("hl2.xml");
  }

  public void testCheckLayoutAttrs() throws Throwable {
    doTestHighlighting("layoutAttrs.xml");
  }

  public void testCheckLayoutAttrs1() throws Throwable {
    doTestHighlighting("layoutAttrs1.xml");
  }

  public void testCheckLayoutAttrs2() throws Throwable {
    doTestHighlighting("layoutAttrs2.xml");
  }

  public void testCheckLayoutAttrs3() throws Throwable {
    doTestHighlighting("layoutAttrs3.xml");
  }

  public void testUnknownAttribute() throws Throwable {
    doTestHighlighting("hl1.xml");
  }

  public void testCustomTagCompletion() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    toTestCompletion("ctn.xml", "ctn_after.xml");
  }

  @SuppressWarnings("ConstantConditions")
  public void testCustomTagCompletion0() throws Throwable {
    final VirtualFile labelViewJava = copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");

    VirtualFile lf1 = myFixture.copyFileToProject(testFolder + '/' + "ctn0.xml", "res/layout/layout1.xml");
    myFixture.configureFromExistingVirtualFile(lf1);
    myFixture.complete(CompletionType.BASIC);
    List<String> variants = myFixture.getLookupElementStrings();
    assertTrue(variants.contains("p1.p2.LabelView"));

    final PsiFile psiLabelViewFile = PsiManager.getInstance(getProject()).findFile(labelViewJava);
    assertInstanceOf(psiLabelViewFile, PsiJavaFile.class);
    final PsiClass labelViewClass = ((PsiJavaFile)psiLabelViewFile).getClasses()[0];
    assertNotNull(labelViewClass);
    myFixture.renameElement(labelViewClass, "LabelView1");

    VirtualFile lf2 = myFixture.copyFileToProject(testFolder + '/' + "ctn0.xml", "res/layout/layout2.xml");
    myFixture.configureFromExistingVirtualFile(lf2);
    myFixture.complete(CompletionType.BASIC);
    variants = myFixture.getLookupElementStrings();
    assertFalse(variants.contains("p1.p2.LabelView"));
    assertTrue(variants.contains("p1.p2.LabelView1"));

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          labelViewJava.delete(null);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });

    VirtualFile lf3 = myFixture.copyFileToProject(testFolder + '/' + "ctn0.xml", "res/layout/layout3.xml");
    myFixture.configureFromExistingVirtualFile(lf3);
    myFixture.complete(CompletionType.BASIC);
    variants = myFixture.getLookupElementStrings();
    assertFalse(variants.contains("p1.p2.LabelView"));
    assertFalse(variants.contains("p1.p2.LabelView1"));
  }

  public void testCustomTagCompletion1() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    copyFileToProject("LabelView1.java", "src/p1/p2/LabelView1.java");
    copyFileToProject("IncorrectView.java", "src/p1/p2/IncorrectView.java");
    doTestCompletionVariants("ctn1.xml", "p2.LabelView", "p2.LabelView1");
  }

  public void testCustomTagCompletion2() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    VirtualFile file = copyFileToProject("ctn2.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("p1\n");
    myFixture.checkResultByFile(testFolder + '/' + "ctn2_after.xml");
  }

  public void testCustomTagCompletion3() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    toTestCompletion("ctn3.xml", "ctn3_after.xml");
  }

  public void testCustomTagCompletion4() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    doTestCompletionVariants("ctn4.xml", "LabelView");
  }

  public void testCustomTagCompletion5() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    VirtualFile file = copyFileToProject("ctn5.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("p1\n");
    myFixture.checkResultByFile(testFolder + '/' + "ctn5_after.xml");
  }

  public void testCustomTagCompletion6() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    toTestCompletion("ctn6.xml", "ctn6_after.xml");
  }

  public void testCustomTagCompletion7() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    toTestCompletion("ctn7.xml", "ctn6_after.xml");
  }

  public void testCustomTagCompletion8() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    copyFileToProject("LabelView1.java", "src/p1/p2/LabelView1.java");
    doTestCompletionVariants("ctn8.xml", "LabelView");
  }

  public void testCustomTagCompletion9() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    toTestCompletion("ctn9.xml", "ctn9_after.xml");
  }

  public void testCustomTagCompletion10() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    copyFileToProject("LabelView1.java", "src/p1/p2/LabelView1.java");
    doTestCompletionVariants("ctn10.xml", "LabelView");
  }

  public void testCustomAttributeNameCompletion() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    doTestCompletionVariants("can.xml", "text", "textColor", "textSize");
  }

  public void testCustomAttributeValueCompletion() throws Throwable {
    doTestCompletionVariants("cav.xml", "@color/color0", "@color/color1", "@color/color2");
  }

  public void testIdea64993() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    doTestHighlighting();
  }

  public void testTagNameCompletion1() throws Throwable {
    toTestCompletion("tn1.xml", "tn1_after.xml");
  }

  public void testFlagCompletion() throws Throwable {
    doTestCompletionVariants("av1.xml", "center", "center_horizontal", "center_vertical");
    doTestCompletionVariants("av2.xml", "fill", "fill_horizontal", "fill_vertical");
  }

  public void testResourceCompletion() throws Throwable {
    doTestCompletionVariants("av3.xml", "@color/", "@android:", "@drawable/");
    doTestCompletionVariants("av8.xml", "@android:", "@anim/", "@color/", "@dimen/", "@drawable/", "@id/", "@layout/", "@string/",
                             "@style/");
  }

  public void testLocalResourceCompletion1() throws Throwable {
    doTestCompletionVariants("av4.xml", "@color/color0", "@color/color1", "@color/color2");
  }

  public void testLocalResourceCompletion2() throws Throwable {
    doTestCompletionVariants("av5.xml", "@drawable/picture1", "@drawable/picture2", "@drawable/picture3", "@drawable/cdrawable");
  }

  public void testLocalResourceCompletion3() throws Throwable {
    doTestCompletionVariants("av7.xml", "@android:", "@string/hello", "@string/hello1", "@string/welcome", "@string/welcome1",
                             "@string/itStr");
  }

  public void testLocalResourceCompletion4() throws Throwable {
    doTestCompletionVariants("av7.xml", "@android:", "@string/hello", "@string/hello1", "@string/welcome", "@string/welcome1",
                             "@string/itStr");
  }

  public void testLocalResourceCompletion5() throws Throwable {
    doTestCompletionVariants("av12.xml", "@android:", "@anim/anim1", "@anim/anim2");
  }

  public void testForceLocalResourceCompletion() throws Throwable {
    doTestCompletionVariants("av13.xml", "@string/hello", "@string/hello1");
  }

  public void testSystemResourceCompletion() throws Throwable {
    doTestCompletionVariants("av6.xml", "@android:color/", "@android:drawable/");
  }

  public void testCompletionSpecialCases() throws Throwable {
    doTestCompletionVariants("av9.xml", "@string/hello", "@string/hello1");
  }

  public void testLayoutAttributeValuesCompletion() throws Throwable {
    doTestCompletionVariants("av10.xml", "fill_parent", "match_parent", "wrap_content", "@android:", "@dimen/myDimen");
    doTestCompletionVariants("av11.xml", "center", "center_horizontal", "center_vertical");
  }

  public void testTagNameCompletion2() throws Throwable {
    doTestCompletionVariants("tn2.xml", "EditText", "ExpandableListView", "ExtractEditText");
  }

  public void testTagNameCompletion3() throws Throwable {
    doTestCompletionVariants("tn3.xml", "View", "ViewAnimator", "ViewFlipper", "ViewStub", "ViewSwitcher");
  }

  /*public void testTagNameCompletion4() throws Throwable {
    toTestCompletion("tn4.xml", "tn4_after.xml");
  }*/

  public void testTagNameCompletion5() throws Throwable {
    toTestCompletion("tn5.xml", "tn5_after.xml");
  }

  public void testTagNameCompletion6() throws Throwable {
    VirtualFile file = copyFileToProject("tn6.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertNotNull(lookupElementStrings);
    assertFalse(lookupElementStrings.contains("android.widget.Button"));
  }

  public void testTagNameCompletion7() throws Throwable {
    toTestCompletion("tn7.xml", "tn7_after.xml");
  }

  public void testTagNameCompletion8() throws Throwable {
    VirtualFile file = copyFileToProject("tn8.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertNotNull(lookupElementStrings);
    assertTrue(lookupElementStrings.contains("widget.Button"));
  }

  public void testTagNameCompletion9() throws Throwable {
    toTestCompletion("tn9.xml", "tn9_after.xml");
  }

  public void testTagNameCompletion10() throws Throwable {
    VirtualFile file = copyFileToProject("tn10.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertNotNull(lookupElementStrings);
    assertFalse(lookupElementStrings.contains("android.widget.Button"));
  }

  public void testTagNameCompletion11() throws Throwable {
    toTestCompletion("tn11.xml", "tn11_after.xml");
  }

  public void testIdCompletion1() throws Throwable {
    doTestCompletionVariants("idcompl1.xml", "@android:", "@+id/", "@id/idd1", "@id/idd2");
  }

  public void testIdCompletion2() throws Throwable {
    doTestCompletionVariants("idcompl2.xml", "@android:id/text1", "@android:id/text2");
  }

  public void testIdHighlighting() throws Throwable {
    doTestHighlighting("idh.xml");
  }

  public void testIdHighlighting1() throws Throwable {
    VirtualFile virtualFile = copyFileToProject("idh.xml", "res/layout-large/idh.xml");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testIdReferenceCompletion() throws Throwable {
    toTestCompletion("idref1.xml", "idref1_after.xml");
  }

  public void testSystemIdReferenceCompletion() throws Throwable {
    toTestCompletion("idref2.xml", "idref2_after.xml");
  }

  public void testSystemResourcesHighlighting() throws Throwable {
    doTestHighlighting("systemRes.xml");
  }

  public void testViewClassCompletion() throws Throwable {
    toTestCompletion("viewclass.xml", "viewclass_after.xml");
  }

  public void testViewElementHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testPrimitiveValues() throws Throwable {
    doTestHighlighting("primValues.xml");
  }

  public void testTableCellAttributes() throws Throwable {
    toTestCompletion("tableCell.xml", "tableCell_after.xml");
  }

  public void testTextViewRootTag_IDEA_62889() throws Throwable {
    toTestCompletion("textViewRootTag.xml", "textViewRootTag_after.xml");
  }

  public void testRequestFocus() throws Throwable {
    toTestCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml");
  }

  public void testMerge() throws Throwable {
    doTestHighlighting("merge.xml");
  }

  public void testFragmentHighlighting() throws Throwable {
    copyFileToProject("MyFragmentActivity.java", "src/p1/p2/MyFragmentActivity.java");
    doTestHighlighting(getTestName(true) + ".xml");
  }

  public void testFragmentCompletion1() throws Throwable {
    copyFileToProject("MyFragmentActivity.java", "src/p1/p2/MyFragmentActivity.java");
    toTestCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml");
  }

  public void testFragmentCompletion2() throws Throwable {
    toTestCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml");
  }

  public void testFragmentCompletion3() throws Throwable {
    toTestCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml");
  }

  /*public void testCustomAttrsPerformance() throws Throwable {
    myFixture.copyFileToProject("dom/resources/bigfile.xml", "res/values/bigfile.xml");
    myFixture.copyFileToProject("dom/resources/bigattrs.xml", "res/values/bigattrs.xml");
    myFixture.copyFileToProject("dom/resources/bigattrs.xml", "res/values/bigattrs1.xml");
    myFixture.copyFileToProject("dom/resources/bigattrs.xml", "res/values/bigattrs2.xml");
    myFixture.copyFileToProject("dom/resources/bigattrs.xml", "res/values/bigattrs3.xml");
    String path = copyFileToProject("bigfile.xml");
    VirtualFile f = myFixture.findFileInTempDir(path);
    myFixture.configureFromExistingVirtualFile(f);
    IdeaTestUtil.assertTiming("", 800, new Runnable() {
      @Override
      public void run() {
        try {
          myFixture.doHighlighting();
        }
        catch (Exception e) {
        }
      }
    });
  }*/

  public void testViewClassReference() throws Throwable {
    VirtualFile file = myFixture.copyFileToProject(testFolder + "/vcr.xml", getPathToCopy("vcr.xml"));
    myFixture.configureFromExistingVirtualFile(file);
    PsiFile psiFile = myFixture.getFile();
    String text = psiFile.getText();
    int rootOffset = text.indexOf("ScrollView");
    PsiReference rootReference = psiFile.findReferenceAt(rootOffset);
    assertNotNull(rootReference);
    PsiElement rootViewClass = rootReference.resolve();
    assertTrue("Must be PsiClass reference", rootViewClass instanceof PsiClass);
    int childOffset = text.indexOf("LinearLayout");
    PsiReference childReference = psiFile.findReferenceAt(childOffset);
    assertNotNull(childReference);
    PsiElement childViewClass = childReference.resolve();
    assertTrue("Must be PsiClass reference", childViewClass instanceof PsiClass);
  }

  public void testViewClassReference1() throws Throwable {
    VirtualFile file = myFixture.copyFileToProject(testFolder + "/vcr1.xml", getPathToCopy("vcr1.xml"));
    myFixture.testHighlighting(true, true, true, file);
  }

  public void testViewClassReference2() throws Throwable {
    VirtualFile file = myFixture.copyFileToProject(testFolder + "/vcr2.xml", getPathToCopy("vcr2.xml"));
    myFixture.configureFromExistingVirtualFile(file);
    PsiFile psiFile = myFixture.getFile();
    String text = psiFile.getText();
    int rootOffset = text.indexOf("ScrollView");
    PsiReference rootReference = psiFile.findReferenceAt(rootOffset);
    assertNotNull(rootReference);
    PsiElement rootViewClass = rootReference.resolve();
    assertTrue("Must be PsiClass reference", rootViewClass instanceof PsiClass);
  }

  public void testOnClickCompletion() throws Throwable {
    copyOnClickClasses();
    doTestCompletionVariants(getTestName(true) + ".xml", "clickHandler1", "clickHandler7");
  }

  public void testOnClickHighlighting() throws Throwable {
    copyOnClickClasses();
    doTestHighlighting();
  }

  public void testMinHeightCompletion() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "@android:", "@dimen/myDimen");
  }

  public void testOnClickNavigation() throws Throwable {
    copyOnClickClasses();
    final VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);

    final PsiReference reference = TargetElementUtilBase.findReference(myFixture.getEditor(), myFixture.getCaretOffset());
    assertNotNull(reference);
    assertInstanceOf(reference, PsiPolyVariantReference.class);
    final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
    assertEquals(3, results.length);
    for (ResolveResult result : results) {
      assertInstanceOf(result.getElement(), PsiMethod.class);
    }
  }

  public void testRelativeIdsCompletion() throws Throwable {
    doTestCompletionVariants(getTestName(false) + ".xml", "@+id/", "@android:", "@id/idd1", "@id/idd2");
  }

  public void testCreateResourceFromUsage() throws Throwable {
    final VirtualFile virtualFile = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    final List<HighlightInfo> infos = myFixture.doHighlighting();
    final List<IntentionAction> actions = new ArrayList<IntentionAction>();

    for (HighlightInfo info : infos) {
      final List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> ranges = info.quickFixActionRanges;

      if (ranges != null) {
        for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : ranges) {
          final IntentionAction action = pair.getFirst().getAction();
          if (action instanceof CreateValueResourceQuickFix) {
            actions.add(action);
          }
        }
      }
    }
    assertEquals(1, actions.size());

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        actions.get(0).invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
      }
    }.execute();
    myFixture.checkResultByFile("res/values/drawables.xml", testFolder + '/' + getTestName(true) + "_drawable_after.xml", true);
  }

  public void testXsdFile1() throws Throwable {
    final VirtualFile virtualFile = copyFileToProject("XsdFile.xsd", "res/raw/XsdFile.xsd");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testXsdFile2() throws Throwable {
    final VirtualFile virtualFile = copyFileToProject("XsdFile.xsd", "res/assets/XsdFile.xsd");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  private void copyOnClickClasses() throws IOException {
    copyFileToProject("OnClick_Class1.java", "src/p1/p2/OnClick_Class1.java");
    copyFileToProject("OnClick_Class2.java", "src/p1/p2/OnClick_Class2.java");
    copyFileToProject("OnClick_Class3.java", "src/p1/p2/OnClick_Class3.java");
    copyFileToProject("OnClick_Class4.java", "src/p1/p2/OnClick_Class4.java");
  }

  public void testJavaCompletion1() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaCompletion("p1.p2");
  }

  public void testJavaCompletion2() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaCompletion("p1.p2");
  }

  public void testJavaCompletion3() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaCompletion("p1.p2");
  }

  public void testJavaIdCompletion() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaCompletion("p1.p2");
  }

  public void testJavaHighlighting1() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaHighlighting("p1.p2");
  }

  public void testJavaHighlighting2() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaHighlighting("p1");
  }

  public void testJavaHighlighting3() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaHighlighting("p1.p2");
  }

  public void testJavaHighlighting4() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaHighlighting("p1.p2");
  }

  public void testJavaHighlighting5() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaHighlighting("p1");
  }

  public void testJavaCreateResourceFromUsage() throws Throwable {
    final VirtualFile virtualFile = copyFileToProject(getTestName(false) + ".java", "src/p1/p2/" + getTestName(true) + ".java");
    doCreateFileResourceFromUsage(virtualFile);
    myFixture.checkResultByFile("res/layout/unknown.xml", testFolder + '/' + getTestName(true) + "_layout_after.xml", true);
  }

  public void testCreateResourceFromUsage1() throws Throwable {
    final VirtualFile virtualFile = copyFileToProject(getTestName(true) + ".xml");
    doCreateFileResourceFromUsage(virtualFile);
    myFixture.type("selector");
    myFixture.checkResultByFile("res/drawable/unknown.xml", testFolder + '/' + getTestName(true) + "_drawable_after.xml", true);
  }

  public void testPrivateAndPublicResources() throws Throwable {
    doTestHighlighting();
  }

  private void doCreateFileResourceFromUsage(VirtualFile virtualFile) {
    myFixture.configureFromExistingVirtualFile(virtualFile);
    final List<HighlightInfo> infos = myFixture.doHighlighting();
    final List<IntentionAction> actions = new ArrayList<IntentionAction>();

    for (HighlightInfo info : infos) {
      final List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> ranges = info.quickFixActionRanges;

      if (ranges != null) {
        for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : ranges) {
          final IntentionAction action = pair.getFirst().getAction();
          if (action instanceof CreateFileResourceQuickFix) {
            actions.add(action);
          }
        }
      }
    }
    assertEquals(1, actions.size());

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        actions.get(0).invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
      }
    }.execute();
  }
}


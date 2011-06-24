package org.jetbrains.android.dom;

import com.android.sdklib.SdkConstants;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;

import java.io.IOException;
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

  public void testCheckLayoutAttrs() throws Throwable {
    doTestHighlighting("layoutAttrs.xml");
  }

  public void testCheckLayoutAttrs1() throws Throwable {
    doTestHighlighting("layoutAttrs1.xml");
  }

  public void testCheckLayoutAttrs2() throws Throwable {
    doTestHighlighting("layoutAttrs2.xml");
  }

  public void testUnknownAttribute() throws Throwable {
    doTestHighlighting("hl1.xml");
  }

  public void testCustomTagCompletion() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    toTestCompletion("ctn.xml", "ctn_after.xml");
  }

  public void testCustomAttributeNameCompletion() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    doTestCompletionVariants("can.xml", "text", "textColor", "textSize");
  }

  public void testCustomAttributeValueCompletion() throws Throwable {
    doTestCompletionVariants("cav.xml", "@color/color0", "@color/color1", "@color/color2");
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
    List<String> list = getAllResources();
    list.add("@android:");
    doTestCompletionVariants("av8.xml", ArrayUtil.toStringArray(list));
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
    doTestCompletionVariants("av10.xml", "fill_parent", "match_parent", "wrap_content", "@android:");
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

  public void testIdCompletion1() throws Throwable {
    doTestCompletionVariants("idcompl1.xml", "@android:", "@+id/", "@id/idd1", "@id/idd2");
  }

  public void testIdCompletion2() throws Throwable {
    doTestCompletionVariants("idcompl2.xml", "@android:id/text", "@android:id/text1", "@android:id/text2");
  }

  public void testIdHighlighting() throws Throwable {
    doTestHighlighting("idh.xml");
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
    int rootOffset = text.indexOf("android.widget.ScrollView");
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

  private void copyOnClickClasses() throws IOException {
    copyFileToProject("OnClick_Class1.java", "src/p1/p2/OnClick_Class1.java");
    copyFileToProject("OnClick_Class2.java", "src/p1/p2/OnClick_Class2.java");
    copyFileToProject("OnClick_Class3.java", "src/p1/p2/OnClick_Class3.java");
    copyFileToProject("OnClick_Class4.java", "src/p1/p2/OnClick_Class4.java");
  }
}


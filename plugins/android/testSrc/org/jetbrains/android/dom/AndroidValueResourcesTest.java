/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.android.dom;

import com.android.SdkConstants;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 25, 2009
 * Time: 6:45:58 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidValueResourcesTest extends AndroidDomTest {
  public AndroidValueResourcesTest() {
    super(false, "dom/resources");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    if (getTestName(true).equals("resOverlay")) {
      return "res-overlay/values/" + testFileName;
    }
    return "res/values/" + testFileName;
  }

  public void testHtmlTags() throws Throwable {
    doTestCompletionVariants("htmlTags.xml", "b", "i", "u");
  }

  public void testStyles1() throws Throwable {
    doTestCompletionVariants("styles1.xml", "@drawable/picture1", "@drawable/picture2", "@drawable/picture3");
  }

  public void testStyles2() throws Throwable {
    toTestCompletion("styles2.xml", "styles2_after.xml");
  }

  public void testStyles3() throws Throwable {
    doTestCompletionVariants("styles3.xml", "normal", "bold", "italic");
  }

  public void testStylesHighlighting() throws Throwable {
    doTestHighlighting("styles4.xml");
  }

  public void testAttrFormatCompletion() throws Throwable {
    toTestCompletion("attrs1.xml", "attrs1_after.xml");
  }

  public void testResourceTypeCompletion() throws Throwable {
    doTestCompletion();
  }

  public void testStyles5() throws Throwable {
    toTestCompletion("styles5.xml", "styles5_after.xml");
  }

  public void testStyles6() throws Throwable {
    toTestCompletion("styles5.xml", "styles5_after.xml");
  }

  public void testStyles7() throws Throwable {
    toTestCompletion("styles5.xml", "styles5_after.xml");
  }

  public void testStyles8() throws Throwable {
    toTestCompletion("styles5.xml", "styles5_after.xml");
  }

  public void testStyles9() throws Throwable {
    toTestCompletion("styles5.xml", "styles5_after.xml");
  }

  public void testStyles10() throws Throwable {
    doTestHighlighting("styles10.xml");
  }

  public void testStylesAttrNameWithoutPrefix() throws Throwable {
    doTestCompletion();
  }

  public void testMoreTypes() throws Throwable {
    doTestHighlighting("moreTypes.xml");
  }

  public void testBool() throws Throwable {
    toTestCompletion("bool.xml", "bool_after.xml");
  }

  public void testBool1() throws Throwable {
    toTestCompletion("bool1.xml", "bool1_after.xml");
  }

  public void testInteger() throws Throwable {
    doTestCompletionVariants("integer.xml", "integer", "integer-array");
  }

  public void testIntegerArray() throws Throwable {
    toTestCompletion("integerArray.xml", "integerArray_after.xml");
  }

  public void testArray() throws Throwable {
    toTestCompletion("array.xml", "array_after.xml");
  }

  public void testPlurals() throws Throwable {
    doTestCompletion();
  }

  public void testPlurals1() throws Throwable {
    doTestCompletion();
  }

  public void testPlurals2() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "zero", "one", "two", "few", "many", "other");
  }

  public void testPlurals3() throws Throwable {
    doTestHighlighting();
  }

  public void testIntResourceReference() throws Throwable {
    myFixture.copyFileToProject(testFolder + "/intResReference.xml", "res/layout/main.xml");
    myFixture.copyFileToProject(testFolder + "/intbool.xml", "res/values/values.xml");
    myFixture.testCompletion("res/layout/main.xml", testFolder + "/intResReference_after.xml");
  }

  public void testBoolResourceReference() throws Throwable {
    myFixture.copyFileToProject(testFolder + "/boolResReference.xml", "res/layout/main.xml");
    myFixture.copyFileToProject(testFolder + "/intbool.xml", "res/values/values.xml");
    myFixture.testCompletion("res/layout/main.xml", testFolder + "/boolResReference_after.xml");
  }

  public void testResourceReferenceAsValueHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testResourceReferenceAsValueCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testResourceReferenceAsValueCompletion2() throws Throwable {
    doTestCompletion();
  }

  public void testResourceReferenceAsValueCompletion3() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "true", "false");
  }

  public void testResourceReferenceAsValueCompletion4() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml");
  }

  public void testParentStyleReference() throws Throwable {
    VirtualFile file = myFixture.copyFileToProject(testFolder + "/psreference.xml", getPathToCopy("psreference.xml"));
    myFixture.configureFromExistingVirtualFile(file);
    PsiFile psiFile = myFixture.getFile();
    String text = psiFile.getText();
    int rootOffset = text.indexOf("android:Theme");
    PsiReference rootReference = psiFile.findReferenceAt(rootOffset);
    assertNotNull(rootReference);
    PsiElement element = rootReference.resolve();
    assertTrue("Must be PsiClass reference", element instanceof XmlAttributeValue);
  }

  // see getPathToCopy()
  public void testResOverlay() throws Throwable {
    doTestCompletionVariants("styles1.xml", "@drawable/picture1", "@drawable/picture2", "@drawable/picture3");
  }

  public void testCreateResourceFromUsage() throws Throwable {
    final VirtualFile virtualFile = copyFileToProject(getTestName(true) + ".xml", "res/values/drawables.xml");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    final List<HighlightInfo> infos = myFixture.doHighlighting();
    final List<IntentionAction> actions = new ArrayList<IntentionAction>();

    for (HighlightInfo info : infos) {
      final List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> ranges = info.quickFixActionRanges;

      if (ranges != null) {
        for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : ranges) {
          actions.add(pair.getFirst().getAction());
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
    myFixture.checkResultByFile(testFolder + '/' + getTestName(true) + "_after.xml", true);
  }
}

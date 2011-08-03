/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.dom;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDrawableResourcesDomTest extends AndroidDomTest {
  public AndroidDrawableResourcesDomTest() {
    super(true, "dom/drawable");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    copyFileToProject("myDrawable.png", "res/drawable/myDrawable.png");
    copyFileToProject("otherResource.xml", "res/values/strings1.xml");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/drawable/" + testFileName;
  }

  public void testStateListHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testStateListCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testStateListCompletion2() throws Throwable {
    doTestCompletion();
  }

  public void testStateListCompletion3() throws Throwable {
    doTestCompletion();
  }

  public void testStateListCompletion4() throws Throwable {
    doTestCompletion();
  }

  public void testStateListCompletion5() throws Throwable {
    doTestCompletion();
  }

  public void testBitmapHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testBitmapHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testBitmapHighlighting3() throws Throwable {
    doTestHighlighting();
  }

  public void testBitmapCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testBitmapCompletion2() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testNinePatchHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testNinePatchHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testNinePatchCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testNinePatchCompletion2() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testLayerListHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testLayerListHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testLayerListCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testLayerListCompletion2() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testLayerListCompletion3() throws Throwable {
    doTestCompletion();
  }

  public void testLevelListHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testLevelListCompletion1() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testLevelListCompletion2() throws Throwable {
    doTestCompletion();
  }

  public void testLevelListCompletion3() throws Throwable {
    doTestCompletion();
  }

  public void testTransitionHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testTransitionHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testTransitionCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testTransitionCompletion2() throws Throwable {
    doTestCompletion();
  }

  public void testInsetHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testInsetHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testInsetCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testInsetCompletion2() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testClipHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testClipCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testClipCompletion2() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testScaleHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testScaleHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testScaleHighlighting3() throws Throwable {
    doTestHighlighting();
  }

  public void testScaleCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testScaleCompletion2() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testShapeHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testShapeCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testShapeCompletion2() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "gradient", "solid", "size", "stroke", "padding", "corners");
  }

  public void testAnimationListHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testAnimationListHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testAnimationListCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testAnimationListCompletion2() throws Throwable {
    doTestCompletion();
  }

  public void testAnimationListCompletion3() throws Throwable {
    doTestCompletion();
  }

  public void testIncorrectRootTag() throws Throwable {
    doTestHighlighting();
  }

  public void testRootTagCompletion() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "selector", "bitmap", "nine-patch", "layer-list", "level-list", "transition",
                             "inset", "clip", "scale", "shape");
  }

  private void doTestOnlyDrawableReferences() throws IOException {
    VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertNotNull(lookupElementStrings);
    for (String s : lookupElementStrings) {
      if (!s.startsWith("@android") && !s.startsWith("@drawable") && !s.startsWith("@color")) {
        fail("Variant " + s + " shouldn't be threre");
      }
    }
  }
}

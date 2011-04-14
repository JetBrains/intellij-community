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

  private void doTestOnlyDrawableReferences() throws IOException {
    VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertNotNull(lookupElementStrings);
    for (String s : lookupElementStrings) {
      if (!s.startsWith("@android") && !s.startsWith("@drawable")) {
        fail("Variant " + s + " shouldn't be threre");
      }
    }
  }

  public void testRootTagCompletion() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "selector", "bitmap", "nine-patch");
  }
}

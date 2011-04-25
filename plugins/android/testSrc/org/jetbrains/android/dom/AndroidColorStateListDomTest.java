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

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidColorStateListDomTest extends AndroidDomTest {
  public AndroidColorStateListDomTest() {
    super(true, "dom/color");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    copyFileToProject("colors.xml", "res/values/colors.xml");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/color/" + testFileName;
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

  public void testRootTagCompletion() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "selector");
  }
}


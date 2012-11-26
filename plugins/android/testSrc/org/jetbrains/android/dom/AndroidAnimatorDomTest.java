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

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAnimatorDomTest extends AndroidDomTest {
  public AndroidAnimatorDomTest() {
    super(false, "dom/animator");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/animator/" + testFileName;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  public void testRootCompletion() throws Throwable {
    toTestCompletion("root.xml", "root_after.xml");
  }

  public void testTagNames() throws Throwable {
    toTestCompletion("tn.xml", "tn_after.xml");
  }

  public void testHighlighting() throws Throwable {
    doTestHighlighting("hl.xml");
  }

  public void testHighlighting1() throws Throwable {
    copyFileToProject("myInterpolator.xml", "res/interpolator/myInterpolator.xml");
    doTestHighlighting("hl1.xml");
  }

  public void testAttributeNames() throws Throwable {
    toTestCompletion("an1.xml", "an1_after.xml");
    toTestCompletion("an2.xml", "an2_after.xml");
  }

  public void testAttributeValues() throws Throwable {
    toTestCompletion("av.xml", "av_after.xml");
  }
}

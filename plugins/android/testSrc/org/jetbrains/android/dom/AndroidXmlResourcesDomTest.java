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

import com.android.sdklib.SdkConstants;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 11, 2009
 * Time: 8:37:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidXmlResourcesDomTest extends AndroidDomTest {
  public AndroidXmlResourcesDomTest() {
    super(false, "dom/xml");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/xml/" + testFileName;
  }

  public void testPreferenceRootCompletion() throws Throwable {
    toTestCompletion("pref1.xml", "pref1_after.xml");
  }

  public void testPreferenceChildrenCompletion() throws Throwable {
    toTestCompletion("pref2.xml", "pref2_after.xml");
  }

  public void testPreferenceAttributeNamesCompletion1() throws Throwable {
    doTestCompletionVariants("pref3.xml", "summary", "summaryOn", "summaryOff");
  }

  public void testPreferenceAttributeNamesCompletion2() throws Throwable {
    toTestCompletion("pref4.xml", "pref4_after.xml");
  }

  public void testPreferenceAttributeValueCompletion() throws Throwable {
    doTestCompletionVariants("pref5.xml", "@string/welcome", "@string/welcome1");
  }

  public void testSearchableRoot() throws Throwable {
    toTestCompletion("searchable_r.xml", "searchable_r_after.xml");
  }

  public void testSearchableAttributeName() throws Throwable {
    toTestCompletion("searchable_an.xml", "searchable_an_after.xml");
  }

  public void testSearchableAttributeValue() throws Throwable {
    doTestCompletionVariants("searchable_av.xml", "@string/welcome", "@string/welcome1");
  }

  public void testSearchableTagNameCompletion() throws Throwable {
    toTestCompletion("searchable_tn.xml", "searchable_tn_after.xml");
  }

  public void testPreferenceIntent() throws Throwable {
    doTestHighlighting("pref_intent.xml");
  }

  public void testPreferenceIntent1() throws Throwable {
    toTestCompletion("pref_intent1.xml", "pref_intent1_after.xml");
  }

  public void testPreferenceWidget() throws Throwable {
    toTestCompletion("pref_widget.xml", "pref_widget_after.xml");
  }

  public void testKeyboard() throws Throwable {
    doTestHighlighting("keyboard.xml");
  }

  public void testKeyboard1() throws Throwable {
    toTestCompletion("keyboard1.xml", "keyboard1_after.xml");
  }

  public void testDeviceAdmin() throws Throwable {
    doTestHighlighting("deviceAdmin.xml");
  }

  public void testDeviceAdmin1() throws Throwable {
    toTestCompletion("deviceAdmin1.xml", "deviceAdmin1_after.xml");
  }

  public void testDeviceAdmin2() throws Throwable {
    toTestCompletion("deviceAdmin2.xml", "deviceAdmin2_after.xml");
  }

  public void testDeviceAdmin3() throws Throwable {
    toTestCompletion("deviceAdmin3.xml", "deviceAdmin3_after.xml");
  }

  public void testAccountAuthenticator() throws Throwable {
    toTestCompletion("accountAuthenticator.xml", "accountAuthenticator_after.xml");
  }

  public void testAccountAuthenticator1() throws Throwable {
    toTestCompletion("accountAuthenticator1.xml", "accountAuthenticator1_after.xml");
  }

  public void testAppwidgetProviderConfigure() throws Throwable {
    copyFileToProject("MyWidgetConfigurable.java", "src/p1/p2/MyWidgetConfigurable.java");
    doTestCompletion();
  }
}

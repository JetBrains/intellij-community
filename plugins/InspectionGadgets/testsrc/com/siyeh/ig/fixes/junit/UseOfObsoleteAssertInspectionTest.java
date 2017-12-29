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
package com.siyeh.ig.fixes.junit;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.junit.UseOfObsoleteAssertInspection;

public class UseOfObsoleteAssertInspectionTest extends IGQuickFixesTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package junit.framework; public class Assert { public static void fail(){}" +
                       "public static void assertEquals(double d1, double d2, double d3) {}" +
                       "public static void assertEquals(Object o1, Object o2) {}" +
                       "}");

    myFixture.addClass("package junit.framework; public class TestCase extends Assert {}");
    myFixture.addClass("package org.junit; public class Assert { " +
                       "public static void fail(){}" +
                       "@Deprecated public static void assertEquals(double d1, double d2) {}" +
                       "            public static void assertEquals(double d1, double d2,  double d3) {}}");

    myFixture.enableInspections(new UseOfObsoleteAssertInspection());
  }

  public void testExtendsTestCase() {
    doFixTest();
  }

  public void testStaticAccess() {
    doFixTest();
  }

  public void testSingleStaticAccess() {
    doFixTest();
  }

  public void testOnDemandStaticImport() {
    doFixTest();
  }

  public void testStaticImport() {
    doFixTest();
  }

  public void testSingleStaticImport() {
    doFixTest();
  }

  public void testAddingDeltaToAvoidFailure() {
    doFixTest();
  }

  private void doFixTest() {
    doTest(getTestName(true), InspectionGadgetsBundle.message("use.of.obsolete.assert.quickfix"));
  }

  @Override
  protected String getRelativePath() {
    return "junit/useOfObsoleteAssert";
  }
}

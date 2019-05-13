/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.style;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.annotation.SingleElementAnnotationInspection;

public class SingleElementAnnotationInspectionTest extends IGQuickFixesTestCase {

  public void testOneAttr() {
    doTest();
  }

  public void testMultiAttr() {
    doTest();
  }

  public void testAlreadyHasName() {
    assertQuickfixNotAvailable();
  }

  public void testAnnotationAttr() {
    doTest();
  }

  public void testArrayAttr() {
    doTest();
  }

  public void testArrayItemAttr() {
    doTest();
  }

  public void testIncompatibleType() {
    assertQuickfixNotAvailable();
  }

  public void testIncompatibleArrayItemType() {
    assertQuickfixNotAvailable();
  }

  public void testNoValueAttr() {
    assertQuickfixNotAvailable();
  }

  public void testNameAlreadyUsed() {
    assertQuickfixNotAvailable();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SingleElementAnnotationInspection());
    myDefaultHint = InspectionGadgetsBundle.message("single.element.annotation.quickfix");
    myRelativePath = "style/expand_annotation";
  }
}

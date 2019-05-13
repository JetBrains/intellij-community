// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.ArrayCanBeReplacedWithEnumValuesInspection;


public class ArrayCanBeReplacedWithEnumValuesFixTest extends IGQuickFixesTestCase {

  public void testClassWithEnum() { doTest("TestEnum"); }

  public void testEnumWithField() {doTest("TestEnum");}

  public void testNotEnumInit() { assertQuickfixNotAvailable(); }

  public void testNotEnumMulti() { assertQuickfixNotAvailable(); }

  public void testEnumRevOrder() { assertQuickfixNotAvailable();}

  public void testErrorInMultiDArray() { assertQuickfixNotAvailable();}

  public void testMultiDArrayNoError() {assertQuickfixNotAvailable();}

  public void testInnerEnum() {doTest("Inner");}

  public void testFooInit() {assertQuickfixNotAvailable();}

  public void testOuterEnumUse() {doTest("TestEnum");}



  @Override
  protected void doTest(String hint) {
    super.doTest(InspectionGadgetsBundle.message("array.can.be.replaced.with.enum.values.quickfix", hint));
  }

  @Override
  protected void assertQuickfixNotAvailable() {
    String message = InspectionGadgetsBundle.message("array.can.be.replaced.with.enum.values.quickfix", "@");
    super.assertQuickfixNotAvailable(message.substring(0, message.indexOf('@')));
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ArrayCanBeReplacedWithEnumValuesInspection());
    myRelativePath = "style/array_replaced_enum_values";
  }


  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "public class OuterEnum {\n" +
      "    public enum TestEnum {\n" +
      "        A, B, C\n" +
      "    }\n" +
      "}"
    };
  }
}


/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
class ConstantIfStatementInspectionTest extends LightInspectionTestCase {

  void testParentheses() {
    doStatementTest("""/*'if' statement can be simplified*/if/**/ (((false))) {
      System.out.println(1);
    } else {
      System.out.println(2);
    }""");
  }

  void "test remove following statements when body always returns via break"() {
    myFixture.configureByText"a.java", """
class Foo {
  void f(){
        while (true) {
            <warning descr="'if' statement can be simplified">i<caret>f</warning> (true) {
                break;//comment
            }
            System.out.println();
        }
  }
}
"""
    myFixture.enableInspections(getInspection())
    myFixture.checkHighlighting()
    myFixture.launchAction(myFixture.findSingleIntention("Simplify"))
    myFixture.checkResult """
class Foo {
  void f(){
        while (true) {
            break;//comment
        }
  }
}
"""
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ConstantIfStatementInspection();
  }
}

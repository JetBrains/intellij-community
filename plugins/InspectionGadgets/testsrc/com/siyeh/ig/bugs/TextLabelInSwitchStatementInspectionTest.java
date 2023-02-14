// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings({"TextLabelInSwitchStatement", "UnusedLabel"})
public class TextLabelInSwitchStatementInspectionTest extends LightJavaInspectionTestCase {

  public void testSwitchStatement() {
    doTest("""
             class Type {
                 void x(E e) {
                     switch (e) {
                         case A, B:
                             /*Text label 'caseZ:' in 'switch' statement*/caseZ/**/: break;
                         case C:
                             break;
                     }
                 }
             }""");
  }

  public void testSwitchExpression() {
    doTest("""
             class Type {
                 void x(E e) {
                     int i = switch (e) {
                         case A,B:
                         /*Text label 'caseZ:' in 'switch' expression*/caseZ/**/: yield 1;
                         case C: yield 0;
                     };
                 }
             }""");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new TextLabelInSwitchStatementInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
enum E {
    A,B,C
}"""
    };
  }
}
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.TodoCommentInspection;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class TodoCommentInspectionTest extends LightJavaInspectionTestCase {

  public void testNoDuplicates() {
    doTest("""
             class A {
             // /*TODO comment 'todo fixme'*/todo fixme/**/
             }""");
  }

  public void testOnlyHighlightLineWithTodo() {
    myFixture.configureByText("X.java", """
                                /**
                                 * Very useful class
                                 * <warning descr="TODO comment 'TODO: to be or not to be'">TODO: to be or not to be</warning>
                                 *
                                 * @author turbanov
                                 */
                                class WithTodo {}""");
    myFixture.testHighlighting(true, false, false);
  }

  public void testOnlyWarnOnEmpty() {
    myFixture.enableInspections(new TodoCommentInspection());
    myFixture.configureByText("X.java", """
      class X {
      //<warning descr="TODO comment without description">TODO</warning>
      //   <warning descr="TODO comment without description">TODO</warning>
      //<warning descr="TODO comment without description">TODO:</warning>
      //<warning descr="TODO comment without description">TODO :</warning>
      //<warning descr="TODO comment without description">TODO-</warning>
      //<warning descr="TODO comment without description">TODO - todo todo</warning>
      //TODO : OK, some description
      }""");
    myFixture.testHighlighting(true, false, false);
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final TodoCommentInspection inspection = new TodoCommentInspection();
    inspection.onlyWarnOnEmpty = false;
    return inspection;
  }
}
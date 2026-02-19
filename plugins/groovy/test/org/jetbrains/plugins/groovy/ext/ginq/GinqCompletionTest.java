// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.completion.GroovyCompletionTestBase;

public class GinqCompletionTest extends GroovyCompletionTestBase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GinqTestUtils.getProjectDescriptor();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new GrUnresolvedAccessInspection(), new GroovyAssignabilityCheckInspection());
  }

  private void completeGinq(String before, String after) {
    doBasicTest("GQ {\n" + before + "\n}", "GQ {\n" + after + "\n}");
  }

  private void completeMethodGinq(String before, String after) {
    String testCode = """
      import groovy.ginq.transform.GQ;
      
      @GQ
      public void foo() {
        %s 
      }""".formatted(before);
    doBasicTest(testCode, testCode.replace(before, after));
  }

  private void noCompleteGinq(String before, String... excluded) {
    doNoVariantsTest("GQ { \n " + before + " \n }", excluded);
  }

  public void testFrom() {
    completeGinq("fro<caret>", "from x in");
  }

  public void testSelect() {
    completeGinq("from x in [1]\nselec<caret>", "from x in [1]\nselect");
  }

  public void testBareJoin() {
    completeGinq("from x in [1]\nfullhashjoi<caret>", "from x in [1]\nfullhashjoin x1 in  on");
  }

  public void testJoin() {
    completeGinq("from x in [1] \nfullhashjoi<caret>\nselect x", "from x in [1] \nfullhashjoin x1 in  on\nselect x");
  }

  public void testWhere() {
    completeGinq("from x in [1] \nwhe<caret>\nselect x", "from x in [1] \nwhere \nselect x");
  }

  public void testOn() {
    completeGinq("from x in [1] \njoin y in [1] o<caret>\nselect x", "from x in [1] \njoin y in [1] on\nselect x");
  }

  public void testAfterCrossjoin() {
    completeGinq("from x in [1] \ncrossjoin y in [1]\nwhe<caret>\nselect x", "from x in [1] \ncrossjoin y in [1]\nwhere \nselect x");
  }

  public void testNoOnAfterCrossjoin() {
    noCompleteGinq("from x in [1] \ncrossjoin y in [1] <caret>\nselect x", "on");
  }

  public void testNoJoinAfterJoin() {
    noCompleteGinq("from x in [1] \njoin y in [1] <caret>\nselect x", "join", "crossjoin");
  }

  public void testCompleteCrossjoin() {
    completeGinq("from x in [1] \ncrossjo<caret>\nselect x", "from x in [1] \ncrossjoin x1 in \nselect x");
  }

  public void testCompleteBindings() {
    completeGinq("from xxxx in [1] \nwhere xxx<caret>\nselect xxxx", "from xxxx in [1] \nwhere xxxx\nselect xxxx");
  }

  public void testCompleteInWindow() {
    completeGinq("from x in [1]\nselect rowNu<caret>", "from x in [1]\nselect (rowNumber() over ())");
  }

  public void testCompleteInWindow2() {
    completeGinq("from x in [1]\nselect firstVal<caret>", "from x in [1]\nselect (firstValue() over ())");
  }

  public void testCompleteInOver() {
    completeGinq("from x in [1]\nselect (rowNumber() over (orde<caret>))", "from x in [1]\nselect (rowNumber() over (orderby ))");
  }

  public void testCompleteInOver2() {
    completeGinq("from x in [1]\nselect (rowNumber() over (parti<caret>))", "from x in [1]\nselect (rowNumber() over (partitionby ))");
  }

  public void testCompleteInOver3() {
    completeGinq("from x in [1]\nselect (rowNumber() over (partitionby x orderb<caret>))",
                 "from x in [1]\nselect (rowNumber() over (partitionby x orderby ))");
  }

  public void testCompleteInOver4() {
    completeGinq("from x in [1]\nselect (rowNumber() over (partitionby x orderby x ro<caret>))",
                 "from x in [1]\nselect (rowNumber() over (partitionby x orderby x rows ))");
  }

  public void testCompleteInner() {
    completeGinq("from nnnn in (from a in [1] innerhashjo<caret> select b)\nselect nnnn",
                 "from nnnn in (from a in [1] innerhashjoin x in  on  select b)\nselect nnnn");
  }

  public void testCompleteAsc() {
    completeGinq("from x in [1]\norderby x in as<caret>\nselect x", "from x in [1]\norderby x in asc\nselect x");
  }

  public void testCompleteDesc() {
    completeGinq("from x in [1]\norderby x in des<caret>\nselect x", "from x in [1]\norderby x in desc\nselect x");
  }

  public void testCompleteAscNullsfirst() {
    completeGinq("from x in [1]\norderby x in asc(nullsf<caret>)\nselect x", "from x in [1]\norderby x in asc(nullsfirst)\nselect x");
  }

  public void testCompleteDescNullslast() {
    completeGinq("from x in [1]\norderby x in desc(nullsla<caret>)\nselect x", "from x in [1]\norderby x in desc(nullslast)\nselect x");
  }

  public void testMethodJoin() {
    completeMethodGinq("from x in [1] \nfullhashjoi<caret>\nselect x", "from x in [1] \nfullhashjoin x1 in  on\nselect x");
  }

  public void testMethodCompleteBindings() {
    completeMethodGinq("from xxxx in [1] \nwhere xxx<caret>\nselect xxxx", "from xxxx in [1] \nwhere xxxx\nselect xxxx");
  }

  public void testMethodCompleteInner() {
    completeMethodGinq("from nnnn in (from a in [1] innerhashjo<caret> select b)\nselect nnnn",
                       "from nnnn in (from a in [1] innerhashjoin x in  on  select b)\nselect nnnn");
  }

  public void testShutdown() {
    doVariantableTest("GQ {\n  shut<caret>\n}", "", CompletionType.BASIC, "shutdown ", "addShutdownHook");
  }

  public void testImmediate() {
    completeGinq("shutdown immedi<caret>", "shutdown immediate");
  }

  public void testAbort() {
    completeGinq("shutdown abor<caret>", "shutdown abort");
  }
}

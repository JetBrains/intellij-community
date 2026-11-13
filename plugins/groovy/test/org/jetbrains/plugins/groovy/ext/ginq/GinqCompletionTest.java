// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.completion.GroovyCompletionFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixtureKt.groovyCompletionFixture;

@TestApplication
public class GinqCompletionTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GinqTestUtils.getProjectDescriptor());
  private final TestFixture<GroovyCompletionFixture> completion = groovyCompletionFixture(project, TestUtils.getAbsoluteTestDataPath());

  @BeforeEach
  public void setUp() {
    completion.get().getFixture().enableInspections(new GrUnresolvedAccessInspection(), new GroovyAssignabilityCheckInspection());
  }

  private void completeGinq(String before, String after) {
    completion.get().doBasicTest("GQ {\n" + before + "\n}", "GQ {\n" + after + "\n}");
  }

  private void completeMethodGinq(String before, String after) {
    String testCode = """
      import groovy.ginq.transform.GQ;
    
      @GQ
      public void foo() {
        %s 
      }""".formatted(before);
    completion.get().doBasicTest(testCode, testCode.replace(before, after));
  }

  private void noCompleteGinq(String before, String... excluded) {
    completion.get().doNoVariantsTest("GQ { \n " + before + " \n }", excluded);
  }

  @Test
  public void testFrom() {
    completeGinq("fro<caret>", "from x in");
  }

  @Test
  public void testSelect() {
    completeGinq("from x in [1]\nselec<caret>", "from x in [1]\nselect");
  }

  @Test
  public void testBareJoin() {
    completeGinq("from x in [1]\nfullhashjoi<caret>", "from x in [1]\nfullhashjoin x1 in  on");
  }

  @Test
  public void testJoin() {
    completeGinq("from x in [1] \nfullhashjoi<caret>\nselect x", "from x in [1] \nfullhashjoin x1 in  on\nselect x");
  }

  @Test
  public void testWhere() {
    completeGinq("from x in [1] \nwhe<caret>\nselect x", "from x in [1] \nwhere \nselect x");
  }

  @Test
  public void testOn() {
    completeGinq("from x in [1] \njoin y in [1] o<caret>\nselect x", "from x in [1] \njoin y in [1] on\nselect x");
  }

  @Test
  public void testAfterCrossjoin() {
    completeGinq("from x in [1] \ncrossjoin y in [1]\nwhe<caret>\nselect x", "from x in [1] \ncrossjoin y in [1]\nwhere \nselect x");
  }

  @Test
  public void testNoOnAfterCrossjoin() {
    noCompleteGinq("from x in [1] \ncrossjoin y in [1] <caret>\nselect x", "on");
  }

  @Test
  public void testNoJoinAfterJoin() {
    noCompleteGinq("from x in [1] \njoin y in [1] <caret>\nselect x", "join", "crossjoin");
  }

  @Test
  public void testCompleteCrossjoin() {
    completeGinq("from x in [1] \ncrossjo<caret>\nselect x", "from x in [1] \ncrossjoin x1 in \nselect x");
  }

  @Test
  public void testCompleteBindings() {
    completeGinq("from xxxx in [1] \nwhere xxx<caret>\nselect xxxx", "from xxxx in [1] \nwhere xxxx\nselect xxxx");
  }

  @Test
  public void testCompleteInWindow() {
    completeGinq("from x in [1]\nselect rowNu<caret>", "from x in [1]\nselect (rowNumber() over ())");
  }

  @Test
  public void testCompleteInWindow2() {
    completeGinq("from x in [1]\nselect firstVal<caret>", "from x in [1]\nselect (firstValue() over ())");
  }

  @Test
  public void testCompleteInOver() {
    completeGinq("from x in [1]\nselect (rowNumber() over (orde<caret>))", "from x in [1]\nselect (rowNumber() over (orderby ))");
  }

  @Test
  public void testCompleteInOver2() {
    completeGinq("from x in [1]\nselect (rowNumber() over (parti<caret>))", "from x in [1]\nselect (rowNumber() over (partitionby ))");
  }

  @Test
  public void testCompleteInOver3() {
    completeGinq("from x in [1]\nselect (rowNumber() over (partitionby x orderb<caret>))",
                 "from x in [1]\nselect (rowNumber() over (partitionby x orderby ))");
  }

  @Test
  public void testCompleteInOver4() {
    completeGinq("from x in [1]\nselect (rowNumber() over (partitionby x orderby x ro<caret>))",
                 "from x in [1]\nselect (rowNumber() over (partitionby x orderby x rows ))");
  }

  @Test
  public void testCompleteInner() {
    completeGinq("from nnnn in (from a in [1] innerhashjo<caret> select b)\nselect nnnn",
                 "from nnnn in (from a in [1] innerhashjoin x in  on  select b)\nselect nnnn");
  }

  @Test
  public void testCompleteAsc() {
    completeGinq("from x in [1]\norderby x in as<caret>\nselect x", "from x in [1]\norderby x in asc\nselect x");
  }

  @Test
  public void testCompleteDesc() {
    completeGinq("from x in [1]\norderby x in des<caret>\nselect x", "from x in [1]\norderby x in desc\nselect x");
  }

  @Test
  public void testCompleteAscNullsfirst() {
    completeGinq("from x in [1]\norderby x in asc(nullsf<caret>)\nselect x", "from x in [1]\norderby x in asc(nullsfirst)\nselect x");
  }

  @Test
  public void testCompleteDescNullslast() {
    completeGinq("from x in [1]\norderby x in desc(nullsla<caret>)\nselect x", "from x in [1]\norderby x in desc(nullslast)\nselect x");
  }

  @Test
  public void testMethodJoin() {
    completeMethodGinq("from x in [1] \nfullhashjoi<caret>\nselect x", "from x in [1] \nfullhashjoin x1 in  on\nselect x");
  }

  @Test
  public void testMethodCompleteBindings() {
    completeMethodGinq("from xxxx in [1] \nwhere xxx<caret>\nselect xxxx", "from xxxx in [1] \nwhere xxxx\nselect xxxx");
  }

  @Test
  public void testMethodCompleteInner() {
    completeMethodGinq("from nnnn in (from a in [1] innerhashjo<caret> select b)\nselect nnnn",
                       "from nnnn in (from a in [1] innerhashjoin x in  on  select b)\nselect nnnn");
  }

  @Test
  public void testShutdown() {
    completion.get().doVariantableTest("GQ {\n  shut<caret>\n}", "", CompletionType.BASIC, "shutdown ", "addShutdownHook");
  }

  @Test
  public void testImmediate() {
    completeGinq("shutdown immedi<caret>", "shutdown immediate");
  }

  @Test
  public void testAbort() {
    completeGinq("shutdown abor<caret>", "shutdown abort");
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.completion.GroovyCompletionTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixture.groovyCompletionFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;

@TestApplication
public class GinqCompletionTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GinqTestUtils.getProjectDescriptor());
  private final TestFixture<GroovyCompletionTestFixture> completion = groovyCompletionFixture(groovy);

  @BeforeEach
  public void setUp() {
    runInEdtAndWait(() -> {
      groovy.get().enableInspections(new GrUnresolvedAccessInspection(), new GroovyAssignabilityCheckInspection());
    });
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
    runInEdtAndWait(() -> {
      completeGinq("fro<caret>", "from x in");
    });
  }

  @Test
  public void testSelect() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1]\nselec<caret>", "from x in [1]\nselect");
    });
  }

  @Test
  public void testBareJoin() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1]\nfullhashjoi<caret>", "from x in [1]\nfullhashjoin x1 in  on");
    });
  }

  @Test
  public void testJoin() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1] \nfullhashjoi<caret>\nselect x", "from x in [1] \nfullhashjoin x1 in  on\nselect x");
    });
  }

  @Test
  public void testWhere() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1] \nwhe<caret>\nselect x", "from x in [1] \nwhere \nselect x");
    });
  }

  @Test
  public void testOn() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1] \njoin y in [1] o<caret>\nselect x", "from x in [1] \njoin y in [1] on\nselect x");
    });
  }

  @Test
  public void testAfterCrossjoin() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1] \ncrossjoin y in [1]\nwhe<caret>\nselect x", "from x in [1] \ncrossjoin y in [1]\nwhere \nselect x");
    });
  }

  @Test
  public void testNoOnAfterCrossjoin() {
    runInEdtAndWait(() -> {
      noCompleteGinq("from x in [1] \ncrossjoin y in [1] <caret>\nselect x", "on");
    });
  }

  @Test
  public void testNoJoinAfterJoin() {
    runInEdtAndWait(() -> {
      noCompleteGinq("from x in [1] \njoin y in [1] <caret>\nselect x", "join", "crossjoin");
    });
  }

  @Test
  public void testCompleteCrossjoin() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1] \ncrossjo<caret>\nselect x", "from x in [1] \ncrossjoin x1 in \nselect x");
    });
  }

  @Test
  public void testCompleteBindings() {
    runInEdtAndWait(() -> {
      completeGinq("from xxxx in [1] \nwhere xxx<caret>\nselect xxxx", "from xxxx in [1] \nwhere xxxx\nselect xxxx");
    });
  }

  @Test
  public void testCompleteInWindow() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1]\nselect rowNu<caret>", "from x in [1]\nselect (rowNumber() over ())");
    });
  }

  @Test
  public void testCompleteInWindow2() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1]\nselect firstVal<caret>", "from x in [1]\nselect (firstValue() over ())");
    });
  }

  @Test
  public void testCompleteInOver() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1]\nselect (rowNumber() over (orde<caret>))", "from x in [1]\nselect (rowNumber() over (orderby ))");
    });
  }

  @Test
  public void testCompleteInOver2() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1]\nselect (rowNumber() over (parti<caret>))", "from x in [1]\nselect (rowNumber() over (partitionby ))");
    });
  }

  @Test
  public void testCompleteInOver3() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1]\nselect (rowNumber() over (partitionby x orderb<caret>))",
                   "from x in [1]\nselect (rowNumber() over (partitionby x orderby ))");
    });
  }

  @Test
  public void testCompleteInOver4() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1]\nselect (rowNumber() over (partitionby x orderby x ro<caret>))",
                   "from x in [1]\nselect (rowNumber() over (partitionby x orderby x rows ))");
    });
  }

  @Test
  public void testCompleteInner() {
    runInEdtAndWait(() -> {
      completeGinq("from nnnn in (from a in [1] innerhashjo<caret> select b)\nselect nnnn",
                   "from nnnn in (from a in [1] innerhashjoin x in  on  select b)\nselect nnnn");
    });
  }

  @Test
  public void testCompleteAsc() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1]\norderby x in as<caret>\nselect x", "from x in [1]\norderby x in asc\nselect x");
    });
  }

  @Test
  public void testCompleteDesc() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1]\norderby x in des<caret>\nselect x", "from x in [1]\norderby x in desc\nselect x");
    });
  }

  @Test
  public void testCompleteAscNullsfirst() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1]\norderby x in asc(nullsf<caret>)\nselect x", "from x in [1]\norderby x in asc(nullsfirst)\nselect x");
    });
  }

  @Test
  public void testCompleteDescNullslast() {
    runInEdtAndWait(() -> {
      completeGinq("from x in [1]\norderby x in desc(nullsla<caret>)\nselect x", "from x in [1]\norderby x in desc(nullslast)\nselect x");
    });
  }

  @Test
  public void testMethodJoin() {
    runInEdtAndWait(() -> {
      completeMethodGinq("from x in [1] \nfullhashjoi<caret>\nselect x", "from x in [1] \nfullhashjoin x1 in  on\nselect x");
    });
  }

  @Test
  public void testMethodCompleteBindings() {
    runInEdtAndWait(() -> {
      completeMethodGinq("from xxxx in [1] \nwhere xxx<caret>\nselect xxxx", "from xxxx in [1] \nwhere xxxx\nselect xxxx");
    });
  }

  @Test
  public void testMethodCompleteInner() {
    runInEdtAndWait(() -> {
      completeMethodGinq("from nnnn in (from a in [1] innerhashjo<caret> select b)\nselect nnnn",
                         "from nnnn in (from a in [1] innerhashjoin x in  on  select b)\nselect nnnn");
    });
  }

  @Test
  public void testShutdown() {
    runInEdtAndWait(() -> {
      completion.get().doVariantableTest("GQ {\n  shut<caret>\n}", "", CompletionType.BASIC, "shutdown ", "addShutdownHook");
    });
  }

  @Test
  public void testImmediate() {
    runInEdtAndWait(() -> {
      completeGinq("shutdown immedi<caret>", "shutdown immediate");
    });
  }

  @Test
  public void testAbort() {
    runInEdtAndWait(() -> {
      completeGinq("shutdown abor<caret>", "shutdown abort");
    });
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.unassignedVariable.UnassignedVariableAccessInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrUnassignedVariableAccessTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_5_0);
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, GrHighlightingFixture.getHighlightingTestDataPath(), () -> new InspectionProfileEntry[]{new UnassignedVariableAccessInspection()});

  @Test
  public void testUnassigned1() { highlighting.get().doTest(); }

  @Test
  public void testUnassigned2() { highlighting.get().doTest(); }

  @Test
  public void testUnassigned3() { highlighting.get().doTest(); }

  @Test
  public void testUnassigned4() { highlighting.get().doTest(); }

  @Test
  public void testUnassignedTryFinally() { highlighting.get().doTest(); }

  @Test
  public void testVarIsNotInitialized() {
    highlighting.get().doTestHighlighting("""
                         def xxx() {
                           def category = null
                           for (def update : updateIds) {
                             def p = update

                             if (something) {
                               category = p
                             }

                             print p
                           }
                         }
                         """);
  }

  @Test
  public void testSimple() {
    highlighting.get().doTestHighlighting("""
                         def bar() {
                           def p
                           print <warning descr="Variable 'p' might not be assigned">p</warning>
                         }
                         """);
  }

  @Test
  public void testAssignedAfterReadInLoop() {
    highlighting.get().doTestHighlighting("""
                         def xxx() {
                           def p
                           for (def update : updateIds) {
                             print <warning descr="Variable 'p' might not be assigned">p</warning>
                             p = 1\s
                           }
                         }
                         """);
  }

  @Test
  public void testUnassignedAccessInCheck() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    UnassignedVariableAccessInspection inspection = new UnassignedVariableAccessInspection();
    inspection.myIgnoreBooleanExpressions = true;

    myFixture.configureByText("_.groovy", """
      def foo
      if (foo) print 'fooo!!!'

      def bar
      if (bar!=null) print 'foo!!!'

      def baz
      if (<warning descr="Variable 'baz' might not be assigned">baz</warning> + 2) print "fooooo!"
      """);
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true);
  }

  @Test
  public void testUnassignedAccessInBooleanExpressionsEnabled() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    UnassignedVariableAccessInspection inspection = new UnassignedVariableAccessInspection();
    inspection.myIgnoreBooleanExpressions = false;
    myFixture.enableInspections(inspection);

    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");

    myFixture.testHighlighting(true, false, true);
  }

  @Test
  public void testUnassignedAccessInBooleanExpressionsDisabled() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    UnassignedVariableAccessInspection inspection = new UnassignedVariableAccessInspection();
    inspection.myIgnoreBooleanExpressions = true;
    myFixture.enableInspections(inspection);

    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");

    myFixture.testHighlighting(true, false, true);
  }

  @Test
  public void testVarNotAssigned() { highlighting.get().doTest(); }

  @Test
  public void testMultipleVarNotAssigned() { highlighting.get().doTest(); }

  @Test
  public void testForLoopWithNestedEndlessLoop() { highlighting.get().doTest(); }

  @Test
  public void testVariableAssignedOutsideForLoop() { highlighting.get().doTest(); }

  @Test
  public void testTryResource() { highlighting.get().doTest(); }
}

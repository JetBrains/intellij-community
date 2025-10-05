// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.unassignedVariable.UnassignedVariableAccessInspection;

/**
 * @author Max Medvedev
 */
public class GrUnassignedVariableAccessTest extends GrHighlightingTestBase {
  @Override
  public InspectionProfileEntry[] getCustomInspections() {
    return new InspectionProfileEntry[]{new UnassignedVariableAccessInspection()};
  }

  public void testUnassigned1() { doTest(); }

  public void testUnassigned2() { doTest(); }

  public void testUnassigned3() { doTest(); }

  public void testUnassigned4() { doTest(); }

  public void testUnassignedTryFinally() { doTest(); }

  public void testVarIsNotInitialized() {
    doTestHighlighting("""
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

  public void testSimple() {
    doTestHighlighting("""
                         def bar() {
                           def p
                           print <warning descr="Variable 'p' might not be assigned">p</warning>
                         }
                         """);
  }

  public void testAssignedAfterReadInLoop() {
    doTestHighlighting("""
                         def xxx() {
                           def p
                           for (def update : updateIds) {
                             print <warning descr="Variable 'p' might not be assigned">p</warning>
                             p = 1\s
                           }
                         }
                         """);
  }

  public void testUnassignedAccessInCheck() {
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

  public void testUnassignedAccessInBooleanExpressionsEnabled() {
    UnassignedVariableAccessInspection inspection = new UnassignedVariableAccessInspection();
    inspection.myIgnoreBooleanExpressions = false;
    myFixture.enableInspections(inspection);

    myFixture.configureByFile(getTestName(false) + ".groovy");

    myFixture.testHighlighting(true, false, true);
  }


  public void testUnassignedAccessInBooleanExpressionsDisabled() {
    UnassignedVariableAccessInspection inspection = new UnassignedVariableAccessInspection();
    inspection.myIgnoreBooleanExpressions = true;
    myFixture.enableInspections(inspection);

    myFixture.configureByFile(getTestName(false) + ".groovy");

    myFixture.testHighlighting(true, false, true);
  }

  public void testVarNotAssigned() { doTest(); }

  public void testMultipleVarNotAssigned() { doTest(); }

  public void testForLoopWithNestedEndlessLoop() { doTest(); }

  public void testVariableAssignedOutsideForLoop() { doTest(); }

  public void testTryResource() { doTest(); }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_5_0;
  }
}

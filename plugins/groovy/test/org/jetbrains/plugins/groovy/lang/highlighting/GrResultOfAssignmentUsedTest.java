// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.InspectionProfileEntry;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyResultOfAssignmentUsedInspection;

public class GrResultOfAssignmentUsedTest extends GrHighlightingTestBase {

  private final GroovyResultOfAssignmentUsedInspection inspection = new GroovyResultOfAssignmentUsedInspection();

  @Override
  public InspectionProfileEntry[] getCustomInspections() {
    return new InspectionProfileEntry[]{inspection};
  }

  public void testUsedVar() {
    doTestHighlighting("""
                         def foo(a) {
                           if ((<warning descr="Usage of assignment expression result">a = 5</warning>) || a) {
                             <warning descr="Usage of assignment expression result">a = 4</warning>
                           }
                         }

                         def foo2(a) {
                           def b = 'b'
                           if (!a) {
                             println b
                             b = 5
                           }
                           return 0 // make b = 5 not a return statement
                         }

                         def bar(a) {
                           print ((<warning descr="Usage of assignment expression result">a = 5</warning>)?:a)
                         }

                         def a(b) {
                           if (2 && (<warning descr="Usage of assignment expression result">b = 5</warning>)) {
                             b
                           }
                         }
                       """);
  }

  public void testResultOfAssignmentUsedInspection() {
    doTestHighlighting("""
                         if ((<warning descr="Usage of assignment expression result">a = b</warning>) == null) {
                         }
                       """);

    doTestHighlighting("""
                         while (<warning descr="Usage of assignment expression result">a = b</warning>) {
                         }
                       """);

    doTestHighlighting("""
                         for (i = 0;<warning descr="Usage of assignment expression result">a = b</warning>; i++) {
                         }
                       """);

    doTestHighlighting("""
                         System.out.println(<warning descr="Usage of assignment expression result">a = b</warning>)
                       """);

    doTestHighlighting("""
                         (<warning descr="Usage of assignment expression result">a = b</warning>                         ).each {}
                       """);
  }

  public void testInspectClosuresOptionIsTrue() {
    inspection.inspectClosures = true;
    doTestHighlighting("a = 1 ");
    doTestHighlighting("a(0, { <warning descr=\"Usage of assignment expression result\">b = 1</warning> }, 2)");
    doTestHighlighting("def a = { <warning descr=\"Usage of assignment expression result\">b = 1</warning> }");
    doTestHighlighting("a(0, <warning descr=\"Usage of assignment expression result\">b = 1</warning>, 2)");
    doTestHighlighting("def a() { <warning descr=\"Usage of assignment expression result\">b = 1</warning> }");
    inspection.inspectClosures = false;
  }

  public void testInspectClosuresOptionIsFalse() {
    inspection.inspectClosures = false;
    doTestHighlighting("a = 1 ");
    doTestHighlighting("a(0, { b = 1 }, 2)");
    doTestHighlighting("def a = { b = 1 }");
    doTestHighlighting("a(0, <warning descr=\"Usage of assignment expression result\">b = 1</warning>, 2)");
    doTestHighlighting("def a() { <warning descr=\"Usage of assignment expression result\">b = 1</warning> }");
  }
}

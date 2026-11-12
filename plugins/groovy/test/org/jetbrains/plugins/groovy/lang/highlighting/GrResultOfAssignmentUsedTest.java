// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyResultOfAssignmentUsedInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

@TestApplication
public class GrResultOfAssignmentUsedTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final GroovyResultOfAssignmentUsedInspection inspection = new GroovyResultOfAssignmentUsedInspection();
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, GrHighlightingFixture.getHighlightingTestDataPath(), () -> new InspectionProfileEntry[]{inspection});

  @Test
  public void testUsedVar() {
    highlighting.get().doTestHighlighting("""
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

  @Test
  public void testResultOfAssignmentUsedInspection() {
    highlighting.get().doTestHighlighting("""
                         if ((<warning descr="Usage of assignment expression result">a = b</warning>) == null) {
                         }
                       """);

    highlighting.get().doTestHighlighting("""
                         while (<warning descr="Usage of assignment expression result">a = b</warning>) {
                         }
                       """);

    highlighting.get().doTestHighlighting("""
                         for (i = 0;<warning descr="Usage of assignment expression result">a = b</warning>; i++) {
                         }
                       """);

    highlighting.get().doTestHighlighting("""
                         System.out.println(<warning descr="Usage of assignment expression result">a = b</warning>)
                       """);

    highlighting.get().doTestHighlighting("""
                         (<warning descr="Usage of assignment expression result">a = b</warning>                         ).each {}
                       """);
  }

  @Test
  public void testInspectClosuresOptionIsTrue() {
    inspection.inspectClosures = true;
    highlighting.get().doTestHighlighting("a = 1 ");
    highlighting.get().doTestHighlighting("a(0, { <warning descr=\"Usage of assignment expression result\">b = 1</warning> }, 2)");
    highlighting.get().doTestHighlighting("def a = { <warning descr=\"Usage of assignment expression result\">b = 1</warning> }");
    highlighting.get().doTestHighlighting("a(0, <warning descr=\"Usage of assignment expression result\">b = 1</warning>, 2)");
    highlighting.get().doTestHighlighting("def a() { <warning descr=\"Usage of assignment expression result\">b = 1</warning> }");
    inspection.inspectClosures = false;
  }

  @Test
  public void testInspectClosuresOptionIsFalse() {
    inspection.inspectClosures = false;
    highlighting.get().doTestHighlighting("a = 1 ");
    highlighting.get().doTestHighlighting("a(0, { b = 1 }, 2)");
    highlighting.get().doTestHighlighting("def a = { b = 1 }");
    highlighting.get().doTestHighlighting("a(0, <warning descr=\"Usage of assignment expression result\">b = 1</warning>, 2)");
    highlighting.get().doTestHighlighting("def a() { <warning descr=\"Usage of assignment expression result\">b = 1</warning> }");
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection;
import org.jetbrains.plugins.groovy.codeInspection.control.finalVar.GrFinalVariableAccessInspection;
import org.jetbrains.plugins.groovy.codeInspection.cs.GrPOJOInspection;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.util.HighlightingTest;

public class GroovyRecordHighlightingTest extends LightGroovyTestCase implements HighlightingTest {
  public void testPojoWithoutCs() {
    highlightingTest("""
                       import groovy.transform.stc.POJO

                       <warning>@POJO</warning>
                       class A {}
                       """, GrPOJOInspection.class);
  }

  public void testRecordDefinition() {
    highlightingTest("""
                       record R(int a) {}""");
  }

  public void testRecordField() {
    highlightingTest("""
                       record R(int a) {}
                       def x = new R(10)
                       x.a()
                       """);
  }

  public void testFinalRecordField() {
    highlightingTest("""
                       record R(int a) {}
                       def x = new R(10)
                       <warning>x.a</warning> = 20
                       """, GrFinalVariableAccessInspection.class);
  }

  public void testDefaultGetter() {
    highlightingTest("""
                       record X(String a) {}

                       def x = new X(a: "200")
                       println x.a()
                       """);
  }

  public void testCustomGetter() {
    highlightingTest("""
                       record X(String a) {
                           String a() {
                               return a + "20"
                           }
                       }

                       def x = new X(a: "200")
                       println x.a()
                       """);
  }

  public void testPrivateRecordField() {
    highlightingTest("""
                       record R(int a) {}
                       def x = new R(10)
                       x.<warning>a</warning>
                       x.a()
                       """, GroovyAccessibilityInspection.class);
  }

  public void testGROOVY_10305() {
    highlightingTest("""
                       record X(String a, int s) {
                           private X {
                               println 20
                           }

                           public X(String a, int s) {}
                       }""");
  }

  public void testSealedRecord() {
    highlightingTest("""
                       <error>sealed</error> record X(int a) {
                       }""");
  }

  public void testCompactConstructor() {
    highlightingTest("""
                       record X(int a) {
                         <error>X</error> {}
                       }
                       """);
  }

  public void testStaticField() {
    highlightingTest("""
                       record X(static int a, String b)
                       {}

                       new X("")
                       """);
  }

  public void testNoAccessorForStaticField() {
    highlightingTest("""
                       record X(static int a, String b)
                       {}

                       new X("").a<warning>()</warning>
                       """, GroovyAssignabilityCheckInspection.class);
  }

  public void testMapConstructor() {
    highlightingTest("""
                       record X(static int a, String b)
                       {}

                       new X(b : "")
                       """);
  }

  public void testNonImmutableField() {
    highlightingTest("""
                       record X(<error>b</error>) {}
                       """);
  }

  public void testForcedImmutableField() {
    highlightingTest("""
                       import groovy.transform.ImmutableOptions

                       @ImmutableOptions(knownImmutables = ['b'])
                       record X(b) {}
                       """);
  }

  public void testDoNotUnnecessarilyLoadFilesContainingRecords() {
    GroovyFileImpl file = (GroovyFileImpl)myFixture.addFileToProject("A.groovy", "record R(int a, String c) {}");
    myFixture.configureByText("b.groovy", "R r = new R(1, \"\")");
    myFixture.checkHighlighting();
    assertFalse(file.isContentsLoaded());
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_4_0_REAL_JDK;
  }
}

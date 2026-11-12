// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection;
import org.jetbrains.plugins.groovy.codeInspection.control.finalVar.GrFinalVariableAccessInspection;
import org.jetbrains.plugins.groovy.codeInspection.cs.GrPOJOInspection;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestApplication
public class GroovyRecordHighlightingTest implements HighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_4_0_REAL_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Override
  public @NotNull LightJavaCodeInsightTestFixture getFixture() {
    return codeInsightFixture.get();
  }

  @Override
  public String getTestName() {
    return LightGroovyFixtures.groovyTestName(codeInsightFixture.get());
  }

  @Test
  public void testPojoWithoutCs() {
    highlightingTest("""
                       import groovy.transform.stc.POJO

                       <warning>@POJO</warning>
                       class A {}
                       """, GrPOJOInspection.class);
  }

  @Test
  public void testRecordDefinition() {
    highlightingTest("""
                       record R(int a) {}""");
  }

  @Test
  public void testRecordField() {
    highlightingTest("""
                       record R(int a) {}
                       def x = new R(10)
                       x.a()
                       """);
  }

  @Test
  public void testFinalRecordField() {
    highlightingTest("""
                       record R(int a) {}
                       def x = new R(10)
                       <error descr="Cannot assign a value to final field 'a'">x.a</error> = 20
                       """, GrFinalVariableAccessInspection.class);
  }

  @Test
  public void testDefaultGetter() {
    highlightingTest("""
                       record X(String a) {}

                       def x = new X(a: "200")
                       println x.a()
                       """);
  }

  @Test
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

  @Test
  public void testPrivateRecordField() {
    highlightingTest("""
                       record R(int a) {}
                       def x = new R(10)
                       x.<warning>a</warning>
                       x.a()
                       """, GroovyAccessibilityInspection.class);
  }

  @Test
  public void testGROOVY_10305() {
    highlightingTest("""
                       record X(String a, int s) {
                           private X {
                               println 20
                           }

                           public X(String a, int s) {}
                       }""");
  }

  @Test
  public void testSealedRecord() {
    highlightingTest("""
                       <error>sealed</error> record X(int a) {
                       }""");
  }

  @Test
  public void testCompactConstructor() {
    highlightingTest("""
                       record X(int a) {
                         <error>X</error> {}
                       }
                       """);
  }

  @Test
  public void testStaticField() {
    highlightingTest("""
                       record X(static int a, String b)
                       {}

                       new X("")
                       """);
  }

  @Test
  public void testNoAccessorForStaticField() {
    highlightingTest("""
                       record X(static int a, String b)
                       {}

                       new X("").a<warning>()</warning>
                       """, GroovyAssignabilityCheckInspection.class);
  }

  @Test
  public void testMapConstructor() {
    highlightingTest("""
                       record X(static int a, String b)
                       {}

                       new X(b : "")
                       """);
  }

  @Test
  public void testNonImmutableField() {
    highlightingTest("""
                       record X(<error>b</error>) {}
                       """);
  }

  @Test
  public void testForcedImmutableField() {
    highlightingTest("""
                       import groovy.transform.ImmutableOptions

                       @ImmutableOptions(knownImmutables = ['b'])
                       record X(b) {}
                       """);
  }

  @Test
  public void testDoNotUnnecessarilyLoadFilesContainingRecords() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFileImpl file = (GroovyFileImpl)myFixture.addFileToProject("A.groovy", "record R(int a, String c) {}");
    myFixture.configureByText("b.groovy", "R r = new R(1, \"\")");
    myFixture.checkHighlighting();
    assertFalse(file.isContentsLoaded());
  }
}

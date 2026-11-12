// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.codeInspection.actions.CleanupAllIntention;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GrUnnecessaryDefModifierInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("public @interface DummyAnno {}");
  }

  @Test
  public void testMethodParameter() {
    doTest(false, """
             def parameter1(<warning descr="Modifier 'def' is not necessary">def</warning> Object a) {}
             
             def parameter2(<warning descr="Modifier 'def' is not necessary">def</warning> a) {}
             
             def parameter3(@DummyAnno <warning descr="Modifier 'def' is not necessary">def</warning> a) {}
             """,
           """
             def parameter1(Object a) {}
             
             def parameter2(a) {}
             
             def parameter3(@DummyAnno a) {}
             """);
  }

  @Test
  public void testMethodParameterTypedOnly() {
    doTest("""
             def parameter1(<warning descr="Modifier 'def' is not necessary">def</warning> Object a) {}
             
             def parameter2(def a) {}
             
             def parameter3(@DummyAnno def a) {}
             """);
  }

  @Test
  public void testLocalVariable() {
    doTest(false, """
             <warning descr="Modifier 'def' is not necessary">def</warning> Object localVar1
             def localVar2
             @DummyAnno <warning descr="Modifier 'def' is not necessary">def</warning> localVar3
             def (int a, b) = [1, 2]
             """,
           """
             Object localVar1
             def localVar2
             @DummyAnno localVar3
             def (int a, b) = [1, 2]
             """);
  }

  @Test
  public void testLocalVariableTypedOnly() {
    doTest("""
             <warning descr="Modifier 'def' is not necessary">def</warning> Object localVar1
             def localVar2
             @DummyAnno def localVar3
             def (int a, b) = [1, 2]
             """);
  }

  @Test
  public void testReturnType() {
    doTest(false, """
             <warning descr="Modifier 'def' is not necessary">def</warning> void returnType1() {}
             
             def returnType2() {}
             
             @DummyAnno
             <warning descr="Modifier 'def' is not necessary">def</warning> returnType3() {}
             """,
           """
             void returnType1() {}
             
             def returnType2() {}
             
             @DummyAnno
             returnType3() {}
             """);
  }

  @Test
  public void testReturnTypeTypedOnly() {
    doTest("""
             <warning descr="Modifier 'def' is not necessary">def</warning> void returnType1() {}
             
             def returnType2() {}
             
             @DummyAnno
             def returnType3() {}
             """);
  }

  @Test
  public void testGenerics() {
    doTest(false, """
             def <T> void generics1() {}
             
             synchronized <warning descr="Modifier 'def' is not necessary">def</warning> <T> void generics2() {}
             
             @DummyAnno
             <warning descr="Modifier 'def' is not necessary">def</warning> <T> void generics3() {}
             """,
           """
             def <T> void generics1() {}
             
             synchronized <T> void generics2() {}
             
             @DummyAnno
             <T> void generics3() {}
             """);
  }

  @Test
  public void testGenericsTypedOnly() {
    doTest("""
             def <T> void generics1() {}
             
             synchronized <warning descr="Modifier 'def' is not necessary">def</warning> <T> void generics2() {}
             
             @DummyAnno
             <warning descr="Modifier 'def' is not necessary">def</warning> <T> void generics3() {}
             """);
  }

  @Test
  public void testConstructor() {
    doTest(false, """
             class A {
               <warning descr="Modifier 'def' is not necessary">def</warning> A() {}
             }
             """,
           """
             class A {
               A() {}
             }
             """);
  }

  @Test
  public void testConstructorTypedOnly() {
    doTest("""
             class A {
               <warning descr="Modifier 'def' is not necessary">def</warning> A() {}
             }
             """);
  }

  @Test
  public void testJavaForEach() {
    doTest(false, """
             def list = []
             for (<warning descr="Modifier 'def' is not necessary">def</warning> Object a : list) {}
             for (def a : list) {}
             for (@DummyAnno <warning descr="Modifier 'def' is not necessary">def</warning> a : list) {}
             """,
           """
             def list = []
             for (Object a : list) {}
             for (def a : list) {}
             for (@DummyAnno a : list) {}
             """);
  }

  @Test
  public void testJavaForEachTypedOnly() {
    doTest("""
             def list = []
             for (<warning descr="Modifier 'def' is not necessary">def</warning> Object a : list) {}
             for (def a : list) {}
             for (@DummyAnno def a : list) {}
             """);
  }

  @Test
  public void testJavaFor() {
    doTest(false, """
             def list = []
             for (<warning descr="Modifier 'def' is not necessary">def</warning> int b = 0; b < 10; b++) {}
             for (def b = 0; b < 10; b++) {}
             for (@DummyAnno <warning descr="Modifier 'def' is not necessary">def</warning> b = 0; b < 10; b++) {}
             """,
           """
             def list = []
             for (int b = 0; b < 10; b++) {}
             for (def b = 0; b < 10; b++) {}
             for (@DummyAnno b = 0; b < 10; b++) {}
             """);
  }

  @Test
  public void testJavaForTypedOnly() {
    doTest("""
             def list = []
             for (<warning descr="Modifier 'def' is not necessary">def</warning> int b = 0; b < 10; b++) {}
             for (def b = 0; b < 10; b++) {}
             for (@DummyAnno def b = 0; b < 10; b++) {}
             """);
  }

  @Test
  public void testForEach() {
    doTest(false, """
             def list = []
             for (<warning descr="Modifier 'def' is not necessary">def</warning> Object c in list) {}
             for (<warning descr="Modifier 'def' is not necessary">def</warning> c in list) {}
             for (@DummyAnno <warning descr="Modifier 'def' is not necessary">def</warning> c in list) {}
             """,
           """
             def list = []
             for (Object c in list) {}
             for (c in list) {}
             for (@DummyAnno c in list) {}
             """);
  }

  @Test
  public void testForEachTypedOnly() {
    doTest("""
             def list = []
             for (<warning descr="Modifier 'def' is not necessary">def</warning> Object c in list) {}
             for (def c in list) {}
             for (@DummyAnno def c in list) {}
             """);
  }

  private void doTest(boolean typed, String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GrUnnecessaryDefModifierInspection inspection = new GrUnnecessaryDefModifierInspection();
    inspection.reportExplicitTypeOnly = typed;
    myFixture.enableInspections(inspection);
    myFixture.configureByText("_.groovy", before);
    myFixture.checkHighlighting();
    if (after != null) {
      myFixture.launchAction(CleanupAllIntention.INSTANCE);
      myFixture.checkResult(after);
    }
  }

  private void doTest(String before) {
    doTest(true, before, null);
  }
}

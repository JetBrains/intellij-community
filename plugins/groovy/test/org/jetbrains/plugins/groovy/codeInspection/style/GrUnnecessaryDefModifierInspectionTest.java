// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.codeInspection.actions.CleanupAllIntention;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;

public class GrUnnecessaryDefModifierInspectionTest extends LightGroovyTestCase {
  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().addClass("public @interface DummyAnno {}");
  }

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

  public void testMethodParameterTypedOnly() {
    doTest("""
             def parameter1(<warning descr="Modifier 'def' is not necessary">def</warning> Object a) {}
             
             def parameter2(def a) {}
             
             def parameter3(@DummyAnno def a) {}
             """);
  }

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

  public void testLocalVariableTypedOnly() {
    doTest("""
             <warning descr="Modifier 'def' is not necessary">def</warning> Object localVar1
             def localVar2
             @DummyAnno def localVar3
             def (int a, b) = [1, 2]
             """);
  }

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

  public void testReturnTypeTypedOnly() {
    doTest("""
             <warning descr="Modifier 'def' is not necessary">def</warning> void returnType1() {}
             
             def returnType2() {}
             
             @DummyAnno
             def returnType3() {}
             """);
  }

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

  public void testGenericsTypedOnly() {
    doTest("""
             def <T> void generics1() {}
             
             synchronized <warning descr="Modifier 'def' is not necessary">def</warning> <T> void generics2() {}
             
             @DummyAnno
             <warning descr="Modifier 'def' is not necessary">def</warning> <T> void generics3() {}
             """);
  }

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

  public void testConstructorTypedOnly() {
    doTest("""
             class A {
               <warning descr="Modifier 'def' is not necessary">def</warning> A() {}
             }
             """);
  }

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

  public void testJavaForEachTypedOnly() {
    doTest("""
             def list = []
             for (<warning descr="Modifier 'def' is not necessary">def</warning> Object a : list) {}
             for (def a : list) {}
             for (@DummyAnno def a : list) {}
             """);
  }

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

  public void testJavaForTypedOnly() {
    doTest("""
             def list = []
             for (<warning descr="Modifier 'def' is not necessary">def</warning> int b = 0; b < 10; b++) {}
             for (def b = 0; b < 10; b++) {}
             for (@DummyAnno def b = 0; b < 10; b++) {}
             """);
  }

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

  public void testForEachTypedOnly() {
    doTest("""
             def list = []
             for (<warning descr="Modifier 'def' is not necessary">def</warning> Object c in list) {}
             for (def c in list) {}
             for (@DummyAnno def c in list) {}
             """);
  }

  private void doTest(boolean typed, String before, String after) {
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

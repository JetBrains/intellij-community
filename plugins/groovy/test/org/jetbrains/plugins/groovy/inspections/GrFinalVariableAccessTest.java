// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.control.finalVar.GrFinalVariableAccessInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrFinalVariableAccessTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_6_0);
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, GrHighlightingFixture.getHighlightingTestDataPath(), () -> new GrFinalVariableAccessInspection[]{new GrFinalVariableAccessInspection()});

  @Test
  public void testSimpleVar() {
    highlighting.get().doTestHighlighting("""
                         final foo = 5
                         <error descr="Cannot assign a value to final variable 'foo'">foo</error> = 7
                         print foo""");
  }

  @Test
  public void testWriteFromDifferentClassOrClosure() {
    highlighting.get().doTestHighlighting("""
                         final var c = 1
                         new Object() {
                             void f00() {
                                 <error descr="Cannot assign a value to final variable 'c'">c</error> = 10\s
                             }
                         }
                         [1, 2].each { <error descr="Cannot assign a value to final variable 'c'">c</error> = it }
                         Runnable r = () -> <error descr="Cannot assign a value to final variable 'c'">c</error> = 10
                         """);
  }

  @Test
  public void testSimpleVal() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    highlighting.get().doTestHighlighting("""
                         val foo = 5
                         <error descr="Cannot assign a value to final variable 'foo'"><caret>foo</error> = 7
                         print foo""");
    myFixture.launchAction(myFixture.findSingleIntention("Make 'foo' not final"));
    myFixture.checkResult("""
                            var foo = 5
                            foo = 7
                            print foo""");
  }

  @Test
  public void testSplitInit() {
    highlighting.get().doTestHighlighting("""                      
                         final foo
                         foo = 7
                         <error descr="Cannot assign a value to final variable 'foo'">foo</error> = 8
                         print foo""");
  }

  @Test
  public void testIf1() {
    highlighting.get().doTestHighlighting("""
                         final foo = 5
                         if (cond) {
                           <error descr="Cannot assign a value to final variable 'foo'">foo</error> = 7
                         }
                         print foo""");
  }

  @Test
  public void testIf2() {
    highlighting.get().doTestHighlighting("""
                         final foo
                         if (cond) {
                           foo = 7
                         }
                         else {
                           foo = 2
                         }
                         <error descr="Cannot assign a value to final variable 'foo'">foo</error> = 1
                         print foo""");
  }

  @Test
  public void testIf3() {
    highlighting.get().doTestHighlighting("""
                         final foo
                         if (cond) {
                           foo = 7
                         }
                         <error descr="Cannot assign a value to final variable 'foo'">foo</error> = 1
                         print foo""");
  }

  @Test
  public void testFor() {
    highlighting.get().doTestHighlighting("""
                         for (a in b) {
                           final x = 5  //all correct
                           print x
                         }""");
  }

  @Test
  public void testFor2() {
    highlighting.get().doTestHighlighting("""
                         final foo = 5
                         for (a in b) {
                           <error descr="Cannot assign a value to final variable 'foo'">foo</error> = 5
                           print foo
                         }""");
  }

  @Test
  public void testFor3() {
    highlighting.get().doTestHighlighting("""
                         for (a in b)
                           final foo = 5  //correct code""");
  }

  @Test
  public void testForParam() {
    highlighting.get().doTestHighlighting("""
                         for (final i : [1, 2]) {
                           <error descr="Cannot assign a value to final variable 'i'">i</error> = 5
                           print i
                         }""");
  }

  @Test
  public void testDuplicatedVar() {
    highlighting.get().doTestHighlighting("""
                         if (cond) {
                           final foo = 5
                           print foo
                         }
                         
                         if (otherCond) {
                           final foo = 2  //correct
                           <error descr="Cannot assign a value to final variable 'foo'">foo</error> = 4
                           print foo
                         }
                         
                         if (anotherCond)
                           final foo = 3 //correct""");
  }

  @Test
  public void testDuplicatedVar2() {
    highlighting.get().doTestHighlighting("""
                         if (cond) {
                           final foo = 5
                           <error descr="Cannot assign a value to final variable 'foo'">foo</error> = 4
                           print foo
                         }
                         
                         if (otherCond) {
                           foo = 4
                           print foo
                         }
                         
                         if (anotherCond)
                           final foo = 3 //correct""");
  }

  @Test
  public void testDuplicatedVar3() {
    highlighting.get().doTestHighlighting("""
                         class X {
                           def bar() {
                             if (cond) {
                               final foo = 5
                               <error descr="Cannot assign a value to final variable 'foo'">foo</error> = 4
                               print foo
                             }
                         
                             if (otherCond) {
                               foo = 4
                               print foo
                             }
                         
                             if (anotherCond)
                               final foo = 3 //correct
                           }
                         }""");
  }

  @Test
  public void testNoDuplicatedMessage() {
    highlighting.get().doTestHighlighting("""
                         class X {
                           void x() {
                             final String a = ''
                             <error descr="Cannot assign a value to final variable 'a'">a</error> = 'str'
                             println a
                           }
                         }
                         final String a = ''
                         <error descr="Cannot assign a value to final variable 'a'">a</error> = 'str'
                         static void foo() {
                             val x = 3
                             <error descr="Cannot assign a value to final variable 'x'">x</error> = 4
                         }
                         """);
  }

  @Test
  public void testFinalField0() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           final <error descr="Field 'foo' might not have been initialized">foo</error>
                         }""");
  }

  @Test
  public void testFinalField1() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           final foo //correct
                         
                           def Foo() {
                             foo = 2
                           }
                         }""");
  }

  @Test
  public void testFinalField2() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           final <error descr="Field 'foo' might not have been initialized">foo</error>
                         
                           def Foo() {
                             foo = 2
                           }
                         
                           def Foo(x) {
                           }
                         }""");
  }

  @Test
  public void testFinalField3() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           final foo;
                         
                           {
                             foo = 3
                           }
                         
                           def Foo() {
                             <error descr="Cannot assign a value to final field 'foo'">foo</error> = 2
                           }
                         
                           def Foo(x) {
                           }
                         }""");
  }

  @Test
  public void testFinalField4() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           final foo //correct
                         
                           def Foo() {
                             foo = 2
                           }
                         
                           def Foo(x) {
                             this()
                           }
                         }""");
  }

  @Test
  public void testFinalField5() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           final <error descr="Field 'foo' might not have been initialized">foo</error> //correct
                         
                           def Foo() {
                         
                           }
                         
                           def Foo(x) {
                             this()
                           }
                         }""");
  }

  @Test
  public void testFinalField6() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           final <error descr="Field 'foo' might not have been initialized">foo</error> //correct
                         
                           <error>def Foo()</error> {
                             this(2)
                           }
                         
                           <error>def Foo(x)</error> {
                             this()
                           }
                         }""");
  }

  @Test
  public void testStaticFinalField0() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           static final foo = 5 //correct
                         }""");
  }

  @Test
  public void testStaticFinalField1() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           static final <error descr="Field 'foo' might not have been initialized">foo</error>
                         }""");
  }

  @Test
  public void testStaticFinalField2() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           static final <error descr="Field 'foo' might not have been initialized">foo</error>
                         
                           static {
                             if (1) {
                               foo = 1
                             }
                           }
                         }""");
  }

  @Test
  public void testStaticFinalField3() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           static final foo //correct
                         
                           static {
                             if (1) {
                               foo = 1
                             }
                             else {
                               foo = -1
                             }
                           }
                         }""");
  }

  @Test
  public void testStaticFinalField4() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           static final foo //correct
                         
                           static {
                             print 1
                           }
                         
                           static {
                             if (1) {
                               foo = 1
                             }
                             else {
                               foo = -1
                             }
                           }
                         }""");
  }

  @Test
  public void testFinalFieldInit0() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           final foo = 0;
                         
                           {
                             <error descr="Cannot assign a value to final field 'foo'">foo</error> = 1
                           }
                         }""");
  }

  @Test
  public void testFinalFieldInit1() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           final foo;
                         
                           {
                             foo = 0
                           }
                         
                           {
                             <error descr="Cannot assign a value to final field 'foo'">foo</error> = 1
                           }
                         }""");
  }

  @Test
  public void testFinalFieldInit2() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           final foo;
                         
                           {
                             foo = 0
                           }
                         
                           Foo(){
                             <error descr="Cannot assign a value to final field 'foo'">foo</error> = 1
                           }
                         }""");
  }

  @Test
  public void testFinalFieldInit3() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           final foo;
                         
                           Foo(x){
                             foo = 0
                           }
                         
                           Foo(){
                             this(1)
                             <error descr="Cannot assign a value to final field 'foo'">foo</error> = 1
                           }
                         }""");
  }

  @Test
  public void testFinalFieldInit4() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           final foo;
                         
                           {
                             foo = 0
                             <error descr="Cannot assign a value to final field 'foo'">foo</error> = 2
                           }
                         
                         }""");
  }

  @Test
  public void testFinalFieldInit5() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           final foo;
                         
                           {
                             if (1) {
                               foo = 0
                             }
                             <error descr="Cannot assign a value to final field 'foo'">foo</error> = 2
                           }
                         
                         }""");
  }

  @Test
  public void testFinalFieldAccess() {
    highlighting.get().doTestHighlighting("""
                         class Foo {
                           public final foo = 5
                         }
                         
                         <error descr="Cannot assign a value to final field 'foo'">new Foo().foo</error> = 3""");
  }

  @Test
  public void testImmutable() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.Immutable
                         
                         @Immutable
                         class Money {
                             String currency
                             int amount
                             private final int <error descr="Field 'privateField' might not have been initialized">privateField</error>
                         
                             void doubleYourMoney() {
                                 <error descr="Cannot assign a value to final field 'amount'">amount</error> *= 2
                             }
                         }
                         
                         def a = new Money('USA', 100)
                         <error descr="Cannot assign a value to final field 'amount'">a.amount</error> = 1000""");
  }

  @Test
  public void testNonFinalField1() {
    highlighting.get().doTestHighlighting("""
                         class A {
                           int foo
                         
                           def A() {
                             foo = 5
                             foo = 5 //correct
                           }
                         }""");
  }

  @Test
  public void testNonFinalField2() {
    highlighting.get().doTestHighlighting("""
                         class A {
                           int foo = 5
                         
                           def A() {
                             foo = 5 //correct
                             foo = 5 //correct
                           }
                         }""");
  }

  @Test
  public void testFieldAndParameterWithTheSameName() {
    highlighting.get().doTestHighlighting("""
                         class Aaa {
                             final int foo
                             final int bar
                         
                             Aaa(int foo, int p) {
                                 this.foo = foo      // this assignment is erroneously reported as invalid
                                 this.bar = p        // this one is not reported
                             }
                         }
                         """);
  }

  @Test
  public void testEnumConstants() {
    highlighting.get().doTestHighlighting("""
                         enum E {
                           abc, cde
                         
                           final int <error descr="Field 'x' might not have been initialized">x</error>
                         }
                         """);
  }

  @Test
  public void testInc() {
    highlighting.get().doTestHighlighting("""
                         class Aaa {
                             final int foo = 0
                         
                             def test(final String p) {
                                 ++<error descr="Cannot assign a value to final field 'foo'">foo</error>
                                 ++<error descr="Cannot assign a value to final parameter 'p'">p</error>
                         
                                 final int i = 0
                                 ++<error descr="Cannot assign a value to final variable 'i'">i</error>
                             }
                         }
                         """);
  }
}

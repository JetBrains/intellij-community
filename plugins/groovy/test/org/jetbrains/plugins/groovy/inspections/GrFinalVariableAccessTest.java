// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections;

import com.intellij.codeInspection.InspectionProfileEntry;
import org.jetbrains.plugins.groovy.codeInspection.control.finalVar.GrFinalVariableAccessInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;

/**
 * @author Max Medvedev
 */
public class GrFinalVariableAccessTest extends GrHighlightingTestBase {
  @Override
  public InspectionProfileEntry[] getCustomInspections() {
    return new GrFinalVariableAccessInspection[]{new GrFinalVariableAccessInspection()};
  }

  public void testSimpleVar() {
    doTestHighlighting("""
                         final foo = 5
                         <warning>foo</warning> = 7
                         print foo""");
  }

  public void testSplitInit() {
    doTestHighlighting("""                      
                         final foo
                         foo = 7
                         <warning>foo</warning> = 8
                         print foo""");
  }

  public void testIf1() {
    doTestHighlighting("""
                         final foo = 5
                         if (cond) {
                           <warning>foo</warning> = 7
                         }
                         print foo""");
  }

  public void testIf2() {
    doTestHighlighting("""
                         final foo
                         if (cond) {
                           foo = 7
                         }
                         else {
                           foo = 2
                         }
                         <warning>foo</warning> = 1
                         print foo""");
  }

  public void testIf3() {
    doTestHighlighting("""
                         final foo
                         if (cond) {
                           foo = 7
                         }
                         <warning>foo</warning> = 1
                         print foo""");
  }

  public void testFor() {
    doTestHighlighting("""
                         for (a in b) {
                           final x = 5  //all correct
                           print x
                         }""");
  }

  public void testFor2() {
    doTestHighlighting("""
                         final foo = 5
                         for (a in b) {
                           <warning>foo</warning> = 5
                           print foo
                         }""");
  }

  public void testFor3() {
    doTestHighlighting("""
                         for (a in b)
                           final foo = 5  //correct code""");
  }

  public void testForParam() {
    doTestHighlighting("""
                         for (final i : [1, 2]) {
                           <warning>i</warning> = 5
                           print i
                         }""");
  }

  public void testDuplicatedVar() {
    doTestHighlighting("""
                         if (cond) {
                           final foo = 5
                           print foo
                         }
                         
                         if (otherCond) {
                           final foo = 2  //correct
                           <warning>foo</warning> = 4
                           print foo
                         }
                         
                         if (anotherCond)
                           final foo = 3 //correct""");
  }

  public void testDuplicatedVar2() {
    doTestHighlighting("""
                         if (cond) {
                           final foo = 5
                           <warning>foo</warning> = 4
                           print foo
                         }
                         
                         if (otherCond) {
                           foo = 4
                           print foo
                         }
                         
                         if (anotherCond)
                           final foo = 3 //correct""");
  }

  public void testDuplicatedVar3() {
    doTestHighlighting("""
                         class X {
                           def bar() {
                             if (cond) {
                               final foo = 5
                               <warning>foo</warning> = 4
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

  public void testFinalField0() {
    doTestHighlighting("""
                         class Foo {
                           final <warning>foo</warning>
                         }""");
  }

  public void testFinalField1() {
    doTestHighlighting("""
                         class Foo {
                           final foo //correct
                         
                           def Foo() {
                             foo = 2
                           }
                         }""");
  }

  public void testFinalField2() {
    doTestHighlighting("""
                         class Foo {
                           final <warning>foo</warning>
                         
                           def Foo() {
                             foo = 2
                           }
                         
                           def Foo(x) {
                           }
                         }""");
  }

  public void testFinalField3() {
    doTestHighlighting("""
                         class Foo {
                           final foo;
                         
                           {
                             foo = 3
                           }
                         
                           def Foo() {
                             <warning>foo</warning> = 2
                           }
                         
                           def Foo(x) {
                           }
                         }""");
  }

  public void testFinalField4() {
    doTestHighlighting("""
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

  public void testFinalField5() {
    doTestHighlighting("""
                         class Foo {
                           final <warning>foo</warning> //correct
                         
                           def Foo() {
                         
                           }
                         
                           def Foo(x) {
                             this()
                           }
                         }""");
  }

  public void testFinalField6() {
    doTestHighlighting("""
                         class Foo {
                           final <warning>foo</warning> //correct
                         
                           <error>def Foo()</error> {
                             this(2)
                           }
                         
                           <error>def Foo(x)</error> {
                             this()
                           }
                         }""");
  }

  public void testStaticFinalField0() {
    doTestHighlighting("""
                         class Foo {
                           static final foo = 5 //correct
                         }""");
  }

  public void testStaticFinalField1() {
    doTestHighlighting("""
                         class Foo {
                           static final <warning>foo</warning>
                         }""");
  }

  public void testStaticFinalField2() {
    doTestHighlighting("""
                         class Foo {
                           static final <warning>foo</warning>
                         
                           static {
                             if (1) {
                               foo = 1
                             }
                           }
                         }""");
  }

  public void testStaticFinalField3() {
    doTestHighlighting("""
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

  public void testStaticFinalField4() {
    doTestHighlighting("""
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

  public void testFinalFieldInit0() {
    doTestHighlighting("""
                         class Foo {
                           final foo = 0;
                         
                           {
                             <warning>foo</warning> = 1
                           }
                         }""");
  }

  public void testFinalFieldInit1() {
    doTestHighlighting("""
                         class Foo {
                           final foo;
                         
                           {
                             foo = 0
                           }
                         
                           {
                             <warning>foo</warning> = 1
                           }
                         }""");
  }

  public void testFinalFieldInit2() {
    doTestHighlighting("""
                         class Foo {
                           final foo;
                         
                           {
                             foo = 0
                           }
                         
                           Foo(){
                             <warning>foo</warning> = 1
                           }
                         }""");
  }

  public void testFinalFieldInit3() {
    doTestHighlighting("""
                         class Foo {
                           final foo;
                         
                           Foo(x){
                             foo = 0
                           }
                         
                           Foo(){
                             this(1)
                             <warning>foo</warning> = 1
                           }
                         }""");
  }

  public void testFinalFieldInit4() {
    doTestHighlighting("""
                         class Foo {
                           final foo;
                         
                           {
                             foo = 0
                             <warning>foo</warning> = 2
                           }
                         
                         }""");
  }

  public void testFinalFieldInit5() {
    doTestHighlighting("""
                         class Foo {
                           final foo;
                         
                           {
                             if (1) {
                               foo = 0
                             }
                             <warning>foo</warning> = 2
                           }
                         
                         }""");
  }

  public void testFinalFieldAccess() {
    doTestHighlighting("""
                         class Foo {
                           public final foo = 5
                         }
                         
                         <warning>new Foo().foo</warning> = 3""");
  }

  public void testImmutable() {
    doTestHighlighting("""
                         import groovy.transform.Immutable
                         
                         @Immutable
                         class Money {
                             String currency
                             int amount
                             private final <warning>privateField</warning>
                         
                             void doubleYourMoney() {
                                 <error>amount</error> *= 2
                             }
                         }
                         
                         def a = new Money('USA', 100)
                         <warning>a.amount</warning> = 1000""");
  }

  public void testNonFinalField1() {
    doTestHighlighting("""
                         class A {
                           int foo
                         
                           def A() {
                             foo = 5
                             foo = 5 //correct
                           }
                         }""");
  }

  public void testNonFinalField2() {
    doTestHighlighting("""
                         class A {
                           int foo = 5
                         
                           def A() {
                             foo = 5 //correct
                             foo = 5 //correct
                           }
                         }""");
  }

  public void test_Field_And_Parameter_With_The_Same_Name() {
    doTestHighlighting("""
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

  public void testEnumConstants() {
    doTestHighlighting("""
                         enum E {
                           abc, cde
                         
                           final int <warning descr="Variable 'x' might not have been initialized">x</warning>
                         }
                         """);
  }

  public void testInc() {
    doTestHighlighting("""
                         class Aaa {
                             final int foo = 0
                         
                             def test(final String p) {
                                 ++<warning>foo</warning>
                                 ++<warning>p</warning>
                         
                                 final int i = 0
                                 ++<warning>i</warning>
                             }
                         }
                         """);
  }
}

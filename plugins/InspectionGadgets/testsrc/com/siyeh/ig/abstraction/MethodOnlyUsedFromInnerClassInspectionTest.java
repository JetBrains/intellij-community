// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("MethodMayBeStatic")
public class MethodOnlyUsedFromInnerClassInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("""
             class X {
               private void /*Method 'foo()'is only used from inner class 'Inner'*/foo/**/() {}
              \s
               class Inner {
                 void test() {foo();}
               }
             }""");
  }

  public void testAnonymous() {
    doTest("""
             class X {
               private int /*Method 'foo()'is only used from an anonymous class derived from 'Inner'*/foo/**/() {return 5;}
              \s
               void test() {
                 new Inner(1) {
                   int x = foo();
                 };
               }
              \s
               static class Inner { Inner(int x) {}}
             }""");
  }

  public void testAnonymousCtor() {
    doTest("""
             class X {
               private int foo() {return 5;}
              \s
               void test() {
                 new Inner(foo()) {
                   int x = 5;
                 };
               }
              \s
               static class Inner { Inner(int x) {}}
             }""");
  }

  public void testLocalClass() {
    doTest("""
             class X {
               private void /*Method 'foo()'is only used from local class 'Local'*/foo/**/() {}
              \s
                void x() {
                 class Local {
                   void test() {foo();}
                 }
               }
             }""");
  }

  public void testNoWarnLocalClass() {
    final MethodOnlyUsedFromInnerClassInspection inspection = new MethodOnlyUsedFromInnerClassInspection();
    inspection.ignoreMethodsAccessedFromAnonymousClass = true;
    myFixture.enableInspections(inspection);
    doTest("""
             class X {
               private void foo() {}
              \s
                void x() {
                 class Local {
                   void test() {foo();}
                 }
               }
             }""");
  }

  @SuppressWarnings({"PublicConstructorInNonPublicClass", "Convert2Lambda"})
  public void testIgnoreUsedOutsideInnerClass() {
    doTest("""
             class OnlyUsedFromAnonymousWarning {

             \t public OnlyUsedFromAnonymousWarning() {
             \t\tRunnable someInterface = new Runnable() {
             \t\t\t@Override
             \t\t\tpublic void run() {
             \t\t\t\tproblemMethod();
             \t\t\t}
             \t\t};
             \t\tproblemMethod();
             \t}

             \tprivate static void problemMethod() {
             \t\tSystem.out.println("Is used not only from anonymous class!");
             \t}
             }""");
  }

  public void testIgnoreStaticMethodCalledFromNonStaticInnerClass() {
    doTest("""
             class Outer {
                 public static void main(String[] args) {
                     class Local {
                         void run(String arg) {
                             if (!isEmpty(arg)) {
                                 System.out.println("Argument is supplied");
                             }
                         }
                     }
                     new Local().run(args[0]);
                 }
                 private static boolean isEmpty(String s) {
                     return s != null && s.trim().isEmpty();
                 }
             }""");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new MethodOnlyUsedFromInnerClassInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
  }
}

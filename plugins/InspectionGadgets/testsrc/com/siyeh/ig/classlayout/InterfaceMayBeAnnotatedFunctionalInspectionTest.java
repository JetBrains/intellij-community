// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class InterfaceMayBeAnnotatedFunctionalInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("interface /*Interface 'Simple' may be annotated with @FunctionalInterface*/Simple/**/ {" +
           "  void f();" +
           "}");
  }

  public void testLessSimple() {
    doTest("""
             interface /*Interface 'F' may be annotated with @FunctionalInterface*/F/**/  {    boolean equals(Object o);    static void f00() {}
                 default void g() {}
                 void f();
             }""");
  }

  public void testNotFunctional() {
    doTest("interface G {" +
           "<T> void f();" +
           "}");
  }

  public void testAlreadyAnnotated() {
    doTest("@FunctionalInterface interface Asd {" +
           "    boolean doSmth();" +
           "}");
  }

  public void testSealed() {
    doTest("""
             sealed interface Fooy {
                 String foo();
             }

             final class X implements Fooy {
                 @Override
                 public String foo() {
                     return null;
                 }
             }""");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }

  public void testAnnotationType() {
    doTest("@interface A {}");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new InterfaceMayBeAnnotatedFunctionalInspection();
  }
}

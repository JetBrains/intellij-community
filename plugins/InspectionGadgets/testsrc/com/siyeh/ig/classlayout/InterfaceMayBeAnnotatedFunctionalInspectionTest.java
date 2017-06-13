// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class InterfaceMayBeAnnotatedFunctionalInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doTest("interface /*Interface 'Simple' may be annotated with @FunctionalInterface*/Simple/**/ {" +
           "  void f();" +
           "}");
  }

  public void testLessSimple() {
    doTest("interface /*Interface 'F' may be annotated with @FunctionalInterface*/F/**/  {" +
           "    boolean equals(Object o);" +
           "    static void f00() {}\n" +
           "    default void g() {}\n" +
           "    void f();\n" +
           "}");
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

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testAnnotationType() {
    doTest("@interface A {}");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new InterfaceMayBeAnnotatedFunctionalInspection();
  }
}

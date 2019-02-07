// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ClassWithOnlyPrivateConstructorsInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doTest("class /*Class 'X' with only 'private' constructors should be declared 'final'*/X/**/ {" +
           "  private X() {}" +
           "  private X(int i) {}" +
           "}");
  }

  public void testExtendingInnerClass() {
    doTest("class X {\n" +
           "  private X() {}\n" +
           "  class Y {\n" +
           "    class Z extends X{}\n" +
           "  }\n" +
           "}");
  }

  public void testNoConstructors() {
    doTest("class X {}");
  }

  public void testNoWarnOnFinalClass() {
    doTest("final class X {" +
           "  private X() {}" +
           "}");
  }
  
  public void testNoWarnOnAnonymInheritor() {
    doTest("class X {" +
           "  private X() {}" +
           "  static {new X() {};} " +
           "}");
  }

  public void testEnum() {
    doTest("enum Currencies {\n" +
           "    EURO, DOLLAR;\n" +
           "    private Currencies() {\n" +
           "    }\n" +
           "}");
  }

  public void testPublicConstructor() {
    doTest("class A {" +
           "  public A() {}" +
           "}");
  }

  public void testSubclassInSameFile() {
    doTest("class Test {" +
           "    private static class Inner {" +
           "        private Inner() {}" +
           "    }" +
           "    private static class InnerSub extends Inner {}" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ClassWithOnlyPrivateConstructorsInspection();
  }
}
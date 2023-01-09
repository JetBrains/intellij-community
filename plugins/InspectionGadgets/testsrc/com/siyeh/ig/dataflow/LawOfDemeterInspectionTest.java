// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class LawOfDemeterInspectionTest extends LightJavaInspectionTestCase {

  public void testSimpleWarning() {
    doTest("import a.*;" +
           "class X {" +
           "  void x(A a) {" +
           "    a.b()./*Call to 'a()' violates Law of Demeter*/a/**/();" +
           "  }" +
           "}");
  }

  public void testFields() {
    doTest("import a.*;" +
           "class X {" +
           "  void x(C c) {" +
           "    c.a./*Call to 'b()' violates Law of Demeter*/b/**/();" +
           "  }" +
           "}");
  }

  public void testFields2() {
    doTest("import a.*;" +
           "class X {" +
           "  void x(D d) {" +
           "    var x = new D().c./*Access of 'a' violates Law of Demeter*/a/**/;" +
           "  }" +
           "}");
  }

  public void testConstructor() {
    doTest("import a.*;" +
           "class X {" +
           "  void x() {" +
           "    new C().a();" +
           "  }" +
           "}");
  }

  public void testFactoryMethod() {
    doTest("import a.*;" +
           "class X {" +
           "  void x() {" +
           "    C.create().a();" +
           "  }" +
           "}");
  }

  public void testReturnsSameClass() {
    doTest("import a.*;" +
           "class X {" +
           "  void x(A a) {" +
           "    a.a().a().a();" +
           "  }" +
           "}");
  }

  public void testInnerClass() {
    doTest("class X {" +
           "  void x() {" +
           "    new Inner().foo().foo();" +
           "  }" +
           "  class Inner {" +
           "    Inner2 foo() {" +
           "      return null;" +
           "    }" +
           "  }" +
           "  class Inner2 {" +
           "    Inner foo() {" +
           "      return null;" +
           "    }" +
           "  }" +
           "}");
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new LawOfDemeterInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package a;" +
      "public interface A {" +
      "  A a();" +
      "  B b();" +
      "}",

      "package a;" +
      "public interface B {" +
      "  A a();" +
      "  B b();" +
      "}",

      "package a;" +
      "public class C {" +
      "  public A a;" +
      "  public B b;" +
      "  public C() {}" +
      "  public static C create() {" +
      "    return new C();" +
      "  }" +
      "  public A a() {" +
      "    return a;" +
      "  }" +
      "  public B b() {" +
      "    return b;" +
      "  }" +
      "}",

      "package a;" +
      "public class D {" +
      "  public C c;" +
      "}"
    };
  }
}
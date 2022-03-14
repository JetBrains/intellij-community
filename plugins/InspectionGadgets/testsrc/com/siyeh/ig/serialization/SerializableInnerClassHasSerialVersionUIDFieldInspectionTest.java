// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class SerializableInnerClassHasSerialVersionUIDFieldInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    return new SerializableInnerClassHasSerialVersionUIDFieldInspection();
  }

  public void testAnonymousClass() {
    doTest("class NonSerializable {" +
           "    public void method() {" +
           "        java.io.Serializable s = new /*Inner class 'java.io.Serializable' does not define a 'serialVersionUID' field*/java.io.Serializable/**/() {};" +
           "    }" +
           "}");
  }

  public void testInnerClass() {
    doTest("class NonSerializable {" +
           "    public class /*Inner class 'MySerializable' does not define a 'serialVersionUID' field*/MySerializable/**/ implements java.io.Serializable {" +
           "    }" +
           "}");
  }

  public void testLocalClass() {
    doTest("class A {" +
           "  void m() {" +
           "    class /*Inner class 'Y' does not define a 'serialVersionUID' field*/Y/**/ implements java.io.Serializable {}" +
           "  }" +
           "}");
  }

  public void testStaticAnonymousClass() {
    doTest("class A {" +
           "  static void m() {" +
           "    new java.io.Serializable() {};" +
           "  }" +
           "}");
  }

  public void testNoWarn() {
    doTest("class A {" +
           "  class B implements java.io.Serializable {" +
           "    private static final long serialVersionUID = -8289890549062901754L;" +
           "  }" +
           "}");
  }

  public void testRecord() {
    doTest("class A {" +
           "  record R() implements java.io.Serializable {" +
           "  }" +
           "}");
  }

  public void testTypeParameter() {
    doTest("class A<TypeParameter extends java.awt.Component> {}");
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.awt;" +
      "public abstract class Component {}"
    };
  }
}

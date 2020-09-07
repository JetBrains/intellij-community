/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

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

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_15;
  }
}

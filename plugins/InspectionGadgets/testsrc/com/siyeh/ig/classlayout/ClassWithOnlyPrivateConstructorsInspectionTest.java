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

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ClassWithOnlyPrivateConstructorsInspection();
  }
}
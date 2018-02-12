/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ObjectEqualsCanBeEqualityInspectionTest extends LightInspectionTestCase {

  public void testClass() {
    doTest("class X {" +
           "  boolean m(Class c1, Class c2) {" +
           "    return c1./*'equals()' can be replaced with '=='*/equals/**/(c2);" +
           "  }" +
           "}");
  }

  public void testObject() {
    doTest("class X {" +
           "  boolean m(Object o1, Object o2) {" +
           "    return o1.equals(o2);" +
           "  }" +
           "}");
  }

  public void testEnum() {
    // should not warn, this is reported by the "'equals()' called on Enum value" inspection
    doTest("class X {" +
           "  boolean m(E e1, E e2) {" +
           "    return e1.equals(e2);" +
           "  }" +
           "}" +
           "enum E {" +
           "  A,B,C" +
           "}");
  }

  public void testString() {
    doTest("class X {" +
           "  boolean isRighteous(String a) {" +
           "    return a.equals(\"righteous\");" +
           "  }" +
           "}");
  }

  public void testSingleton() {
    doTest("class Singleton {" +
           "  private static final Singleton INSTANCE = new Singleton();" +
           "  private Singleton() {}" +
           "}" +
           "class U {" +
           "  boolean f(Singleton s1, Singleton s2) {" +
           "    return s1./*'equals()' can be replaced with '=='*/equals/**/(s2);" +
           "  }" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ObjectEqualsCanBeEqualityInspection();
  }
}
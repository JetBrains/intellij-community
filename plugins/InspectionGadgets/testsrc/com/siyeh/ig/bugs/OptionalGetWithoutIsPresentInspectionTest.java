/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class OptionalGetWithoutIsPresentInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doTest("import java.util.Optional;" +
           "class X {" +
           "  void a(Optional<String> o) {" +
           "    System.out.println(o./*'Optional.get()' without 'isPresent()' check*/get/**/());" +
           "  }" +
           "}");
  }

  public void testOptionalDouble() {
    doTest("import java.util.OptionalDouble;" +
           "class X {" +
           "  double a(OptionalDouble d) {" +
           "    return d./*'OptionalDouble.getAsDouble()' without 'isPresent()' check*/getAsDouble/**/();" +
           "  }" +
           "}");
  }

  public void testOptionalWithoutVariable() {
    doTest("import java.util.Optional;" +
           "class X {" +
           "    {" +
           "        System.out.println(getName()./*'Optional.get()' without 'isPresent()' check*/get/**/());" +
           "    }" +
           "    Optional<String> getName() {" +
           "        return Optional.empty();" +
           "    }" +
           "}");
  }

  public void testWhile1() {
    doTest("import java.util.Optional;" +
           "class X {" +
           "  void m() {" +
           "    Optional<String> o = Optional.empty();" +
           "    while (!o.isPresent()){" +
           "      o = Optional.of(\"\");" +
           "    }" +
           "    o.get();" +
           "  }" +
           "}");
  }

  public void testWhile2() {
    doTest("import java.util.Optional;" +
           "class X {" +
           "  void m() {" +
           "    Optional<String> o = Optional.empty();" +
           "    while (o.isPresent()) {" +
           "      o.get();" +
           "    }" +
           "  }" +
           "}");
  }

  public void testPolyadicExpression1() {
    doTest("import java.util.Optional;" +
           "class X {" +
           "  public void demo(Optional<String> value) {\n" +
           "    boolean flag = value.isPresent() && \"Yes\".equals(value.get());\n" +
           "  }" +
           "}");
  }

  public void testPolyadicExpression2() {
    doTest("import java.util.Optional;" +
           "class X {" +
           "  boolean m(Optional<String> o) {" +
           "    return !o.isPresent() || o.get().equals(\"j\");" +
           "  }" +
           "}");
  }

  public void testPolyadicExpression3() {
    doTest("import java.util.Optional;" +
           "class X {" +
           "  String g() {" +
           "    Optional<String> o = Optional.empty();" +
           "    if(o == null || !o.isPresent()) {" +
           "      return \"\";" +
           "    }" +
           "    return o.get();" +
           "  }" +
           "}");
  }

  public void testOptionalGetWithoutIsPresent() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new OptionalGetWithoutIsPresentInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util;" +
      "public final class Optional<T> {" +
      "  public T get() {" +
      "    return null;" +
      "  }" +
      "  public boolean isPresent() {" +
      "    return true;" +
      "  }" +
      "  public static<T> Optional<T> empty() {" +
      "    return new Optional<>();" +
      "  }" +
      "  public static <T> Optional<T> of(T value) {" +
      "    return new Optional<>(value);" +
      "  }" +
      "}",

      "package java.util;" +
      "public final class OptionalDouble {" +
      "  public boolean isPresent() {" +
      "    return true;" +
      "  }" +
      "  public double getAsDouble() {" +
      "    return 0.0;" +
      "  }" +
      "}",

      "package org.junit;" +
      "public class Assert {" +
      "  public static void assertTrue(boolean b) {}" +
      "}",

      "package org.testng;" +
      "public class Assert {" +
      "  public static void assertTrue(boolean b) {}" +
      "}"
    };
  }
}
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
package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class AssertEqualsMayBeAssertSameInspectionTest extends LightInspectionTestCase {


  public void testSimple() {
    doTest("class Test {" +
           "  @org.junit.Test" +
           "  public void test() {" +
           "    org.junit.Assert./*'assertEquals()' may be 'assertSame()'*/assertEquals/**/(A.a, A.b);" +
           "  }" +
           "}");
  }

  public void testDelegatingAssertMethods() {
    //noinspection ALL
    doTest("class Test extends junit.framework.TestCase {" +
           "  public void testOne() {" +
           "    /*'assertEquals()' may be 'assertSame()'*/assertEquals/**/(A.a, A.b);" +
           "  }" +
           "}");
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package org.junit;" +
      "import java.lang.annotation.ElementType;" +
      "import java.lang.annotation.Retention;" +
      "import java.lang.annotation.RetentionPolicy;" +
      "import java.lang.annotation.Target;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target({ElementType.METHOD})" +
      "public @interface Test {}",

      "package org.junit;" +
      "public class Assert {" +
      "  static public void assertEquals(Object expected, Object actual) {}" +
      "}",

      "package junit.framework;" +
      "public class Assert {" +
      "  public static void assertEquals(Object expected, Object actual) {}" +
      "}",

      "package junit.framework;" +
      "public abstract class TestCase extends Assert {" +
      "  public static void assertEquals(Object expected, Object actual) {" +
      "    Assert.assertEquals(expected, actual);" +
      "  }" +
      "}",

      "final class A {" +
      "  public static final A a = new A();" +
      "  public static final A b = a;" +
      "}"
    };
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AssertEqualsMayBeAssertSameInspection();
  }
}
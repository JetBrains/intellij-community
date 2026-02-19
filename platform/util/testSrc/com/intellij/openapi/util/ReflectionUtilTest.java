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

package com.intellij.openapi.util;

import com.intellij.util.ReflectionUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

public class ReflectionUtilTest extends TestCase {

  @SuppressWarnings("unused")
  public void testFindField() throws Exception {
    class A {
      private String privateA;
      public String publicA;
    }
    class B extends A {
      private String privateB;
      public String publicB;
      private int privateA;
    }

    final Field field = ReflectionUtil.findField(B.class, String.class, "privateA");
    assertNotNull(field);
    assertEquals(String.class, field.getType());
    assertEquals("privateA", field.getName());

    try {
      ReflectionUtil.findField(B.class, String.class, "whatever");
    }
    catch (NoSuchFieldException e) {
      return;
    }
    fail();
  }

  @SuppressWarnings("FieldMayBeFinal")
  public void testResetField() throws NoSuchFieldException {
    class Reset {
      String STRING;
      boolean BOOLEAN;
      int INT;
      double DOUBLE;
      float FLOAT;

      static String STATIC_STRING;
    }

    final Reset reset = new Reset();

    reset.STRING = "value";
    resetField(reset, String.class, "STRING");
    assertNull(reset.STRING);

    reset.BOOLEAN = true;
    resetField(reset, boolean.class, "BOOLEAN");
    assertFalse(reset.BOOLEAN);

    reset.INT = 1;
    resetField(reset, int.class, "INT");
    assertEquals(0, reset.INT);

    reset.DOUBLE = 1;
    resetField(reset, double.class, "DOUBLE");
    assertEquals(0d, reset.DOUBLE);

    reset.FLOAT = 1;
    resetField(reset, float.class, "FLOAT");
    assertEquals(0f, reset.FLOAT);

    Reset.STATIC_STRING = "value";
    ReflectionUtil.resetField(Reset.class, String.class, "STATIC_STRING");
    assertNull(Reset.STATIC_STRING);
  }

  private static void resetField(@NotNull Object object, @Nullable("null means any type") Class<?> type, @NotNull @NonNls String name)
    throws NoSuchFieldException {
    ReflectionUtil.resetField(object, ReflectionUtil.findField(object.getClass(), type, name));
  }

  @SuppressWarnings({"RedundantMethodOverride", "override"})
  public void testHasOverridenMethod() {
    interface A {
      default void method() { }
    }
    interface B extends A {
      // Use method() implementation from A
    }
    abstract class C implements A {
      public void method() { }
    }
    class D implements B {
      public void method() { }
    }
    class E extends C {
      // Use method() implementation from C
    }
    class F extends C {
      public void method() { }
    }

    assertFalse("Method `A.method` isn't overridden in interface B",
                ReflectionUtil.hasOverriddenMethod(B.class, A.class, "method"));
    assertTrue("Method `A.method` is overridden in abstract class C",
               ReflectionUtil.hasOverriddenMethod(C.class, A.class, "method"));
    assertTrue("Method `A.method` is overridden in class D",
               ReflectionUtil.hasOverriddenMethod(D.class, A.class, "method"));
    assertTrue("Method `A.method` isn't directly overridden in class E. " +
               "However, it is indirectly overridden in super class C",
               ReflectionUtil.hasOverriddenMethod(E.class, A.class, "method"));
    assertTrue("Method `A.method` is overridden in class F",
               ReflectionUtil.hasOverriddenMethod(F.class, A.class, "method"));
    assertFalse("Method `C.method` isn't overridden in class E",
                ReflectionUtil.hasOverriddenMethod(E.class, C.class, "method"));
    assertTrue("Method `C.method` is overridden in class F",
               ReflectionUtil.hasOverriddenMethod(F.class, C.class, "method"));

    // Corner cases
    assertFalse("Method `A.method` isn't overridden in interface A",
                ReflectionUtil.hasOverriddenMethod(A.class, A.class, "method"));
    assertFalse("Method `B.method` isn't overridden in interface B",
                ReflectionUtil.hasOverriddenMethod(B.class, B.class, "method"));
    assertFalse("Method `C.method` isn't overridden in interface C",
                ReflectionUtil.hasOverriddenMethod(C.class, C.class, "method"));
  }
}

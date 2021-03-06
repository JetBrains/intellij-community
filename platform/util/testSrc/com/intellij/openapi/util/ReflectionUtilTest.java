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

  public void testFindField() throws Exception {
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

  public void testResetField() throws NoSuchFieldException {
    final Reset reset = new Reset();

    resetField(reset, String.class, "STRING");
    assertNull(reset.STRING);

    resetField(reset, boolean.class, "BOOLEAN");
    assertFalse(reset.BOOLEAN);

    resetField(reset, int.class, "INT");
    assertEquals(0, reset.INT);

    resetField(reset, double.class, "DOUBLE");
    assertEquals(0d, reset.DOUBLE);

    resetField(reset, float.class, "FLOAT");
    assertEquals(0f, reset.FLOAT);

    ReflectionUtil.resetField(Reset.class, String.class, "STATIC_STRING");
    assertNull(Reset.STATIC_STRING);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Reset.STATIC_STRING = "value";
  }

  private static void resetField(@NotNull Object object, @Nullable("null means any type") Class<?> type, @NotNull @NonNls String name)
    throws NoSuchFieldException {
    ReflectionUtil.resetField(object, ReflectionUtil.findField(object.getClass(), type, name));
  }

  static class Reset {
    String STRING = "value";
    boolean BOOLEAN = true;
    int INT = 1;
    double DOUBLE = 1;
    float FLOAT = 1;

    static String STATIC_STRING = "value";
  }

  static class A {
    private String privateA;
    public String publicA;
  }

  static class B extends A{
    private String privateB;
    public String publicB;
    private int privateA;
  }
}

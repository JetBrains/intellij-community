/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import junit.framework.TestCase;
import com.intellij.util.ReflectionUtil;

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

  public void testResetField() throws Exception {
    final Reset reset = new Reset();

    ReflectionUtil.resetField(reset, String.class, "STRING");
    assertNull(reset.STRING);

    ReflectionUtil.resetField(reset, boolean.class, "BOOLEAN");
    assertFalse(reset.BOOLEAN);

    ReflectionUtil.resetField(reset, int.class, "INT");
    assertEquals(0, reset.INT);

    ReflectionUtil.resetField(reset, double.class, "DOUBLE");
    assertEquals(0d, reset.DOUBLE);

    ReflectionUtil.resetField(reset, float.class, "FLOAT");
    assertEquals(0f, reset.FLOAT);

    ReflectionUtil.resetField(Reset.class, String.class, "STATIC_STRING");
    assertNull(Reset.STATIC_STRING);
  }


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Reset.STATIC_STRING = "value";
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

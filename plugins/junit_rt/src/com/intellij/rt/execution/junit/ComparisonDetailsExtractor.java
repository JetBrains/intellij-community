// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.execution.junit;

import junit.framework.ComparisonFailure;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class ComparisonDetailsExtractor  {
  private static final Map<Class<?>, Field> EXPECTED = new HashMap<>();
  private static final Map<Class<?>, Field> ACTUAL = new HashMap<>();
  protected final String myActual;
  protected final String myExpected;
  private final Throwable myAssertion;

  public ComparisonDetailsExtractor(Throwable assertion, String expected, String actual) {
    myAssertion = assertion;
    myActual = actual;
    myExpected = expected;
  }

  static {
    try {
      init(ComparisonFailure.class);
      init(org.junit.ComparisonFailure.class);
    }
    catch (Throwable e) {
      System.err.println(e.getMessage());
    }
  }

  private static void init(Class<?> exceptionClass) throws NoSuchFieldException {
    final Field expectedField = exceptionClass.getDeclaredField("fExpected");
    expectedField.setAccessible(true);
    EXPECTED.put(exceptionClass, expectedField);

    final Field actualField = exceptionClass.getDeclaredField("fActual");
    actualField.setAccessible(true);
    ACTUAL.put(exceptionClass, actualField);
  }

  public static String getActual(Throwable assertion) throws IllegalAccessException, NoSuchFieldException {
    return get(assertion, ACTUAL, "fActual");
  }

  public static String getExpected(Throwable assertion) throws IllegalAccessException, NoSuchFieldException {
    return get(assertion, EXPECTED, "fExpected");
  }

  private static String get(final Throwable assertion, final Map<Class<?>, Field> staticMap, final String fieldName)
    throws IllegalAccessException, NoSuchFieldException {
    String actual;
    if (assertion instanceof ComparisonFailure) {
      actual = (String)staticMap.get(ComparisonFailure.class).get(assertion);
    }
    else if (assertion instanceof org.junit.ComparisonFailure) {
      actual = (String)staticMap.get(org.junit.ComparisonFailure.class).get(assertion);
    }
    else {
      Field field = assertion.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      actual = (String)field.get(assertion);
    }
    return actual;
  }
}

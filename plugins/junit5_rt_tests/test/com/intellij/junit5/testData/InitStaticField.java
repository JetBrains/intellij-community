// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5.testData;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class InitStaticField {
  @SuppressWarnings({"JUnitMalformedDeclaration", "NewClassNamingConvention"})
  public static class MyTest extends BaseTest {
    private static final ResourceClass resourceClass = ResourceClass.instance();

    @Test
    void testOne() {
      System.out.println("one: " + resourceClass.getMessage());
    }

    @Test
    void testTwo() {
      System.out.println("two: " + resourceClass.getMessage());
    }
  }

  public static class BaseTest {
    @BeforeAll
    public static void setup() {
      System.out.println("before");
      ResourceClass.init("init");
    }
  }

  public static class ResourceClass {
    private static ResourceClass INSTANCE;

    private final String message;

    public static ResourceClass instance() {
      return INSTANCE;
    }

    public static void init(String message) {
      INSTANCE = new ResourceClass(message);
    }

    private ResourceClass(String message) {
      this.message = message;
    }

    public String getMessage() {
      return message;
    }
  }
}

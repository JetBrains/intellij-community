// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import org.jetbrains.idea.devkit.inspections.internal.UseCoupleInspection;

public abstract class UseCoupleFixTestBase extends LightDevKitInspectionFixTestBase {

  protected static final String CONVERT_TO_COUPLE_OF_FIX_NAME = "Replace with 'Couple.of()'";
  protected static final String CONVERT_TO_COUPLE_TYPE_FIX_NAME = "Replace with 'Couple<String>'";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UseCoupleInspection());
    myFixture.addClass("""
      package com.intellij.openapi.util;
      public class Pair<A, B> {
        public static <A, B> Pair<A, B> create(A first, B second) { return null; }
        public static <A, B> Pair<A, B> pair(A first, B second) { return null; }
      }
      """);
    myFixture.addClass("""
      package com.intellij.openapi.util;
      public class Couple<T> extends Pair<T, T> {
        public static <T> Couple<T> of(T first, T second) { return null; }
      }
      """);
  }
}

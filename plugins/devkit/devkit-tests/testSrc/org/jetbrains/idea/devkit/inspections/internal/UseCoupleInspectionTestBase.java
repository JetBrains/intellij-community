// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class UseCoupleInspectionTestBase extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
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
    myFixture.enableInspections(new UseCoupleInspection());
  }

  @NotNull
  protected abstract String getFileExtension();

  protected void doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension());
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.groovy.testFramework;

import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.testFramework.UsefulTestCase.assertEquals;
import static com.intellij.testFramework.UsefulTestCase.assertNotNull;
import static com.intellij.testFramework.UsefulTestCase.assertNull;

public final class GroovyAssertions {

  private GroovyAssertions() { }

  public static void assertType(@Nullable String expected, @Nullable PsiType actual) {
    if (expected == null) {
      assertNull(actual);
      return;
    }

    assertNotNull(actual);
    if (actual instanceof PsiIntersectionType) {
      assertEquals(expected, genIntersectionTypeText((PsiIntersectionType)actual));
    }
    else {
      assertEquals(expected, actual.getCanonicalText());
    }
  }

  private static String genIntersectionTypeText(PsiIntersectionType t) {
    return Stream.of(t.getConjuncts()).map(c -> c.getCanonicalText()).collect(Collectors.joining(",", "[", "]"));
  }
}

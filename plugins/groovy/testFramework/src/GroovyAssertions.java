// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.groovy.testFramework;

import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public final class GroovyAssertions {

  private GroovyAssertions() { }

  public static <T> T assertOneElement(@NotNull Collection<? extends T> collection) {
    assertEquals(1, collection.size(), collection::toString);
    return collection.iterator().next();
  }

  public static <T> T assertOneElement(T @NotNull [] array) {
    assertEquals(1, array.length, () -> Arrays.toString(array));
    return array[0];
  }

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

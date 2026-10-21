// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.containers;

import com.intellij.diff.util.Enumerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnumeratorTest {
  @Test
  public void test() {
    Enumerator enumerator = new Enumerator(10);
    int[] indecies = enumerator.enumerate(new Object[]{"a", "b", "a"},0, 0);
    assertTrue(Arrays.equals(new int[]{1, 2, 1}, indecies));
    indecies = enumerator.enumerate(new Object[]{"a", "c", "b"},0,0);
    assertTrue(Arrays.equals(new int[]{1, 3, 2}, indecies));
  }

  @Test
  public void testWithShift() {
    Enumerator enumerator = new Enumerator(10);
    int[] indecies = enumerator.enumerate(new Object[]{"1","a", "b", "a", "2"}, 1, 1);
    assertTrue(Arrays.equals(new int[]{1, 2, 1}, indecies));
  }
}

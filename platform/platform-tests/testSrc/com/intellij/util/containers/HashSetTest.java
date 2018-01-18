// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.runners.Parameterized.*;


@RunWith(value = Parameterized.class)
public class HashSetTest {
  private String mapType;

  public HashSetTest(String mapType) {
    this.mapType = mapType;
  }

  @Parameters
  public static Collection<String> data() {
    return Arrays.asList("jdk", "idea");
  }

  @Test
  public void contains() {
    Set<Integer> set = freshSet();

    assertFalse(set.contains(1));
    assertFalse(set.contains(null));

    set.add(1);
    assertTrue(set.contains(1));


    set.clear();
    assertFalse(set.contains(1));
    assertFalse(set.contains(null));
  }

  @Test
  public void containsAll() {
    Set<Integer> set = freshSet();

    boolean contains = set.containsAll(Collections.emptyList());

    assertTrue(contains);

    contains = set.containsAll(new java.util.HashSet<>());

    assertTrue(contains);

    contains = set.containsAll(Collections.singleton(1));

    assertFalse(contains);

    contains = set.containsAll(Collections.singletonList(1));

    assertFalse(contains);
  }

  @Test
  public void clear() {
    Set<Integer> set = freshSet();

    set.clear();
    assertThat(set).isEmpty();

    set.add(1);
    assertThat(set).isNotEmpty();

    set.clear();
    assertThat(set).isEmpty();
  }

  @NotNull
  private Set<Integer> freshSet() {
    if ("jdk".equals(mapType)) {
      return new java.util.HashSet<>();
    }
    return new com.intellij.util.containers.HashSet<>();
  }
}
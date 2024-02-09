// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.vcs.log.graph.utils;

import com.intellij.vcs.log.graph.utils.impl.ListIntToIntMap;
import com.intellij.vcs.log.graph.utils.impl.PredicateFlags;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class ListIntToIntMapTest extends UpdatableIntToIntMapTest {
  @Override
  protected UpdatableIntToIntMap createUpdatableIntToIntMap(@NotNull Predicate<? super Integer> thisIsVisible, int longSize) {
    return ListIntToIntMap.newInstance(new PredicateFlags(thisIsVisible, longSize), 3);
  }
}

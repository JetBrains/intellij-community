// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public abstract class TreePrintCondition implements Predicate<String>, Condition<String> {
  public abstract static class SetBased extends TreePrintCondition {
    protected Set<String> mySet = new HashSet<>();

    public SetBased(String... elements) {
      ContainerUtil.addAll(mySet, elements);
    }

    @Override
    public final boolean value(String s) {
      return test(s);
    }
  }

  public static class Include extends SetBased {
    public Include(String... elements) {
      super(elements);
    }

    @Override
    public boolean test(String s) {
      return mySet.contains(s);
    }
  }

  public static class Exclude extends SetBased {
    public Exclude(String... elements) {
      super(elements);
    }

    @Override
    public boolean test(String s) {
      return !mySet.contains(s);
    }
  }
}

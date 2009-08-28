package com.intellij.openapi.vcs.impl;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CollectionsDelta {
  private CollectionsDelta() {
  }

  @Nullable
  public static <T> Set<T> notInSecond(final Collection<T> first, final Collection<T> second) {
    Set<T> notInSecond = null;
    for (T topItem : first) {
      if (! second.contains(topItem)) {
        if (notInSecond == null) {
          notInSecond = new HashSet<T>();
        }
        notInSecond.add(topItem);
      }
    }
    return notInSecond;
  }
}

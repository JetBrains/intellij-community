// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.ValueContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntPredicate;

public final class MergedValueContainer<Value> extends ValueContainer<Value> {
  private final @NotNull List<? extends ValueContainer<Value>> myContainers;
  private int mySize;

  public MergedValueContainer(@NotNull List<? extends ValueContainer<Value>> containers) {
    if (containers.isEmpty()) {
      throw new IllegalArgumentException();
    }
    myContainers = containers;
  }

  @Override
  public @NotNull InvertedIndexValueIterator<Value> getValueIterator() {
    return new InvertedIndexValueIterator<Value>() {
      int myNextId = 1;
      ValueIterator<Value> myCurrent = myContainers.get(0).getValueIterator();

      @Override
      public @NotNull IntIterator getInputIdsIterator() {
        return myCurrent.getInputIdsIterator();
      }

      @Override
      public @Nullable IntPredicate getValueAssociationPredicate() {
        return myCurrent.getValueAssociationPredicate();
      }

      @Override
      public Object getFileSetObject() {
        return null;
      }

      @Override
      public boolean hasNext() {
        while (true) {
          if (myCurrent.hasNext()) return true;
          if (myNextId < myContainers.size()) {
            myCurrent = myContainers.get(myNextId++).getValueIterator();
          } else {
            return false;
          }
        }
      }

      @Override
      public Value next() {
        return myCurrent.next();
      }
    };
  }

  @Override
  public int size() {
    if (mySize == 0) {
      mySize = myContainers.stream().mapToInt(c -> c.size()).sum();
    }
    return mySize;
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.ValueContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MergedValueContainer<Value> extends ValueContainer<Value> {
  private final @NotNull List<? extends ValueContainer<Value>> myContainers;
  private int mySize;

  public MergedValueContainer(@NotNull List<? extends ValueContainer<Value>> containers) {
    if (containers.isEmpty()) {
      throw new IllegalArgumentException();
    }
    myContainers = containers;
  }

  @NotNull
  @Override
  public InvertedIndexValueIterator<Value> getValueIterator() {
    return new InvertedIndexValueIterator<Value>() {
      int myNextId = 1;
      ValueIterator<Value> myCurrent = myContainers.get(0).getValueIterator();

      @NotNull
      @Override
      public IntIterator getInputIdsIterator() {
        return myCurrent.getInputIdsIterator();
      }

      @Nullable
      @Override
      public IntPredicate getValueAssociationPredicate() {
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

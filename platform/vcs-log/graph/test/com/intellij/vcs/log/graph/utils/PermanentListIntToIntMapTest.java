// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.utils;

import com.intellij.vcs.log.graph.utils.impl.PermanentListIntToIntMap;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class PermanentListIntToIntMapTest extends UpdatableIntToIntMapTest {
  @Override
  protected UpdatableIntToIntMap createUpdatableIntToIntMap(final @NotNull Predicate<? super Integer> thisIsVisible, final int longSize) {
    return new UpdatableIntToIntMapWrapper(new Flags() {
      @Override
      public int size() {
        return longSize;
      }

      @Override
      public boolean get(int index) {
        return thisIsVisible.test(index);
      }

      @Override
      public void set(int index, boolean value) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void setAll(boolean value) {
        throw new UnsupportedOperationException();
      }
    });
  }

  private static final class UpdatableIntToIntMapWrapper implements UpdatableIntToIntMap {
    @NotNull private final Flags myFlags;
    private IntToIntMap myIntToIntMap;

    private UpdatableIntToIntMapWrapper(@NotNull Flags flags) {
      myFlags = flags;
      createIntToIntMap();
    }

    private void createIntToIntMap() {
      int shortSize = 0;
      for (int i = 0; i < myFlags.size(); i++) {
        if (myFlags.get(i)) shortSize++;
      }
      myIntToIntMap = PermanentListIntToIntMap.newInstance(myFlags, shortSize, 2);
    }

    @Override
    public void update(int startLongIndex, int endLongIndex) {
      createIntToIntMap();
    }

    @Override
    public int shortSize() {
      return myIntToIntMap.shortSize();
    }

    @Override
    public int longSize() {
      return myIntToIntMap.longSize();
    }

    @Override
    public int getLongIndex(int shortIndex) {
      return myIntToIntMap.getLongIndex(shortIndex);
    }

    @Override
    public int getShortIndex(int longIndex) {
      return myIntToIntMap.getShortIndex(longIndex);
    }
  }
}

/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.graph.utils;

import com.intellij.util.BooleanFunction;
import com.intellij.vcs.log.graph.utils.impl.PermanentListIntToIntMap;
import org.jetbrains.annotations.NotNull;

public class PermanentListIntToIntMapTest extends UpdatableIntToIntMapTest {
  @Override
  protected UpdatableIntToIntMap createUpdatableIntToIntMap(@NotNull final BooleanFunction<Integer> thisIsVisible, final int longSize) {
    return new UpdatableIntToIntMapWrapper(new Flags() {
      @Override
      public int size() {
        return longSize;
      }

      @Override
      public boolean get(int index) {
        return thisIsVisible.fun(index);
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

  private static class UpdatableIntToIntMapWrapper implements UpdatableIntToIntMap {
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

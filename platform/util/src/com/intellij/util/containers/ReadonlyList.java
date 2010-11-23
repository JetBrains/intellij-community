/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.util.containers;

import java.util.ArrayList;
import java.util.List;

/**
 * @author irengrig
 */
public interface ReadonlyList<T> {
  T get(final int idx);
  int getSize();

  ReadonlyList EMPTY = new ReadonlyList() {
    @Override
    public Object get(int idx) {
      return null;
    }

    @Override
    public int getSize() {
      return 0;
    }
  };

  public static class ArrayListWrapper<T> implements ReadonlyList<T> {
    private final List<T> myDelegate;

    public ArrayListWrapper(List<T> delegate) {
      myDelegate = delegate;
    }

    public ArrayListWrapper() {
      myDelegate = new ArrayList<T>();
    }

    @Override
    public T get(int idx) {
      return myDelegate.get(idx);
    }

    @Override
    public int getSize() {
      return myDelegate.size();
    }

    public List<T> getDelegate() {
      return myDelegate;
    }
  }
}

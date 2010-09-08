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
package com.intellij.openapi.editor.impl.softwrap;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Generic {@link DataProvider} implementation that assumes that target data is available as list.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Aug 31, 2010 12:56:58 PM
 */
public abstract class AbstractListBasedDataProvider<K extends Comparable<? super K>, V> extends AbstractDataProvider<K, V> {

  private final List<V> myData;
  private int myIndex;

  public AbstractListBasedDataProvider(@NotNull K key, @NotNull List<V> data) {
    super(key);

    // We use given collection instead of copying it to private list because profiling indicates that as an expensive operation
    // when performed frequently.
    myData = data;
  }

  @Override
  protected V doGetData() {
    if (myIndex < myData.size()) {
      return myData.get(myIndex);
    }
    return null;
  }

  @Override
  public boolean next() {
    return ++myIndex < myData.size();
  }

  @Override
  public void advance(int sortingKey) {

    // We inline binary search here because profiling indicates that as a performance boost.
    int start = myIndex;
    int end = myData.size() - 1;

    // We inline binary search here because profiling indicates that it becomes bottleneck to use Collections.binarySearch().
    while (start <= end) {
      int i = (end + start) >>> 1;
      V data = myData.get(i);
      if (getSortingKey(data) < sortingKey) {
        start = i + 1;
        continue;
      }
      if (getSortingKey(data) > sortingKey) {
        end = i - 1;
        continue;
      }

      myIndex = i;
      return;
    }
    myIndex = start;
  }

  @Override
  public int getSortingKey() {
    if (myIndex < myData.size()) {
      return getSortingKey(myData.get(myIndex));
    }
    return 0;
  }

  /**
   * Sub-classes are expected to be able to derive sorting key for the every data instance.
   *
   * @param data    target data which sorting key should be returned
   * @return        sorting key for the given data
   */
  protected abstract int getSortingKey(@NotNull V data);
}

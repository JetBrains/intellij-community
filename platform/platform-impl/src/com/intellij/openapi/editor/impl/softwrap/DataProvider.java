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

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * Defines contract for providing data sorted by int values.
 * <p/>
 * Expected usage is very similar to the {@link Iterator}'s one - the same {@link #getData() current data} and
 * {@link #getSortingKey() sorting key} are exposed until {@link #next()} is called.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 *
 * @author Denis Zhdanov
 * @since Aug 31, 2010 10:56:14 AM
 */
public interface DataProvider<K extends Comparable<? super K>, V> {

  /**
   * @return    current data if any is available; <code>null</code> otherwise
   */
  @Nullable
  Pair<K, V> getData();

  /**
   * @return    sorting key for the {@link #getData() current data}
   */
  int getSortingKey();

  /**
   * Asks current data provider to advance to the next data if any.
   *
   * @return    flag that shows if current provider has more data, i.e. {@link #getData()} is guaranteed to return
   *            not-<code>null</code> value if this method returns <code>true</code>
   */
  boolean next();

  /**
   * Asks current provider to ignore all data which {@link DataProvider#getSortingKey() sorting keys} are less than the given value.
   *
   * @param sortingKey    min sorting key to use
   */
  void advance(int sortingKey);
}

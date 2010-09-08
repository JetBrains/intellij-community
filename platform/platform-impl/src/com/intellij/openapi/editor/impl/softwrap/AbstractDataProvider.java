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

/**
 * Abstract super class for {@link DataProvider} implementations that use the same key all the time.
 *
 * @author Denis Zhdanov
 * @since Aug 31, 2010 12:19:32 PM
 */
public abstract class AbstractDataProvider<K extends Comparable<? super K>, V> implements DataProvider<K, V> {

  private final K myKey;

  public AbstractDataProvider(K key) {
    myKey = key;
  }

  @Nullable
  @Override
  public Pair<K, V> getData() {
    V data = doGetData();
    return data == null ? null : new Pair<K, V>(myKey, data);
  }

  /**
   * @return    current data
   */
  @Nullable
  protected abstract V doGetData();
}

/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;

public class BiDirectionalEnumerator<T> extends Enumerator<T> {
  @NotNull private final TIntObjectHashMap<T> myIntToObjectMap;

  public BiDirectionalEnumerator(int expectNumber, @NotNull TObjectHashingStrategy<T> strategy) {
    super(expectNumber, strategy);

    myIntToObjectMap = new TIntObjectHashMap<T>(expectNumber);
  }

  @Override
  public int enumerateImpl(T object) {
    int index = super.enumerateImpl(object);
    myIntToObjectMap.put(Math.max(index, -index), object);
    return index;
  }

  @Override
  public void clear() {
    super.clear();
    myIntToObjectMap.clear();
  }

  @NotNull
  public T getValue(int index) {
    T value = myIntToObjectMap.get(index);
    if (value == null) {
      throw new RuntimeException("Can not find value by index " + index);
    }
    return value;
  }

  public void forEachValue(@NotNull TObjectProcedure<T> procedure) {
    myIntToObjectMap.forEachValue(procedure);
  }
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.io;

import gnu.trove.TIntObjectHashMap;

import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Dmitry.Shtukenberg
 */
public class WriteableTIntObjectMapAdapter <V> implements WriteableMap<V> {
  private final TIntObjectHashMap<V> hashmap;
  private int[] hashkeys;

  public WriteableTIntObjectMapAdapter(TIntObjectHashMap<V> map) {
    hashmap = map;
  }

  public int[] getHashCodesArray() {
    return hashkeys = hashmap.keys();
  }

  public V getValue(int pos) {
    return hashmap.get(hashkeys[pos]);
  }

  public int getKeyLength(int pos) {
    return 4;
  }

  public void writeKey(DataOutput out, int pos) throws IOException {
    out.writeInt(hashkeys[pos]);
  }
}

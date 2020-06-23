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
package com.intellij.util.indexing.impl;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class CollectionDataExternalizer<K> implements DataExternalizer<Collection<K>> {
  private final DataExternalizer<K> myDataExternalizer;

  public CollectionDataExternalizer(@NotNull DataExternalizer<K> dataExternalizer) {
    myDataExternalizer = dataExternalizer;
  }

  @Override
  public void save(@NotNull DataOutput out, @NotNull Collection<K> value) throws IOException {
    DataInputOutputUtil.writeINT(out, value.size());
    for (K key : value) {
      myDataExternalizer.save(out, key);
    }
  }

  @NotNull
  @Override
  public Collection<K> read(@NotNull DataInput in) throws IOException {
    int size = DataInputOutputUtil.readINT(in);
    if (size == 0) {
      return Collections.emptyList();
    }
    if (size == 1) {
      return Collections.singletonList(myDataExternalizer.read(in));
    }
    List<K> list = new ArrayList<>(size);
    for (int idx = 0; idx < size; idx++) {
      list.add(myDataExternalizer.read(in));
    }
    return list;
  }
}

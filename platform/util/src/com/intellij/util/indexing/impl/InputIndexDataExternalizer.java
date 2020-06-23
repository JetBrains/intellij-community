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

import com.intellij.util.indexing.IndexId;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;

public final class InputIndexDataExternalizer<K> implements DataExternalizer<Collection<K>> {
  private final DataExternalizer<Collection<K>> myKeyCollectionExternalizer;
  private final IndexId<K, ?> myIndexId;

  public InputIndexDataExternalizer(KeyDescriptor<K> keyDescriptor, IndexId<K, ?> indexId) {
    myKeyCollectionExternalizer = new CollectionDataExternalizer<>(keyDescriptor);
    myIndexId = indexId;
  }

  @Override
  public void save(@NotNull DataOutput out, @NotNull Collection<K> value) throws IOException {
    try {
      myKeyCollectionExternalizer.save(out, value);
    }
    catch (IllegalArgumentException e) {
      throw new IOException("Error saving data for index " + myIndexId, e);
    }
  }

  @NotNull
  @Override
  public Collection<K> read(@NotNull DataInput in) throws IOException {
    try {
      return myKeyCollectionExternalizer.read(in);
    }
    catch (IllegalArgumentException e) {
      throw new IOException("Error reading data for index " + myIndexId, e);
    }
  }
}

/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.util.indexing.IndexExtension;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public abstract class KeyCollectionBasedForwardIndex<Key, Value> extends MapBasedForwardIndex<Key, Value, Collection<Key>> {
  protected KeyCollectionBasedForwardIndex(IndexExtension<Key, Value, ?> indexExtension) throws IOException {
    super(indexExtension);
  }

  @Override
  protected InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, Collection<Key> keys) throws IOException {
    return new CollectionInputDataDiffBuilder<Key, Value>(inputId, keys);
  }

  @Override
  protected Collection<Key> convertToMapValueType(int inputId, Map<Key, Value> map) throws IOException {
    return map.keySet();
  }
}

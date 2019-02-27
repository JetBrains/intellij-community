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
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public class KeyCollectionForwardIndexAccessor<Key, Value, Input> extends AbstractForwardIndexAccessor<Key, Value, Collection<Key>, Input> {

  public KeyCollectionForwardIndexAccessor(@NotNull IndexExtension<Key, Value, Input> extension) {
    super(new InputIndexDataExternalizer<>(extension.getKeyDescriptor(), extension.getName()));
  }

  public KeyCollectionForwardIndexAccessor(@NotNull DataExternalizer<Collection<Key>> keysExternalizer) {
    super(keysExternalizer);
  }

  @Nullable
  @Override
  protected Collection<Key> convertToDataType(@Nullable Map<Key, Value> map, @Nullable Input input) {
    return map == null ? null : map.keySet();
  }

  @Nullable
  @Override
  protected Collection<Key> getKeysFromData(@Nullable Collection<Key> keys) {
    return keys;
  }
}
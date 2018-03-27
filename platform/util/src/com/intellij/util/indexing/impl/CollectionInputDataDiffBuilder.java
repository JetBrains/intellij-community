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

import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@ApiStatus.Experimental
public class CollectionInputDataDiffBuilder<Key, Value> extends InputDataDiffBuilder<Key,Value> {
  private final Collection<Key> mySeq;

  public CollectionInputDataDiffBuilder(int inputId, @Nullable Collection<Key> seq) {
    super(inputId);
    mySeq = seq == null ? Collections.<Key>emptySet() : seq;
  }

  @Override
  public boolean differentiate(@NotNull Map<Key, Value> newData,
                            @NotNull KeyValueUpdateProcessor<Key, Value> addProcessor,
                            @NotNull KeyValueUpdateProcessor<Key, Value> updateProcessor,
                            @NotNull RemovedKeyProcessor<Key> removeProcessor) throws StorageException {
    return differentiateWithKeySeq(mySeq, newData, myInputId, addProcessor, removeProcessor);
  }

  public Collection<Key> getSeq() {
    return mySeq;
  }

  static <Key, Value> boolean differentiateWithKeySeq(@NotNull Collection<Key> currentData,
                                                   @NotNull Map<Key, Value> newData,
                                                   int inputId,
                                                   @NotNull KeyValueUpdateProcessor<Key, Value> addProcessor,
                                                   @NotNull RemovedKeyProcessor<Key> removeProcessor) throws StorageException {
    for (Key key : currentData) {
      removeProcessor.process(key, inputId);
    }
    EmptyInputDataDiffBuilder.processKeys(newData, addProcessor, inputId);
    return true;
  }
}

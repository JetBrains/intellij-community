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

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public class EmptyInputKeyIterator<Key, Value> implements ForwardIndex.InputKeyIterator<Key,Value> {
  public static final EmptyInputKeyIterator EMPTY_INPUT_KEY_ITERATOR = new EmptyInputKeyIterator();

  public static <Key, Value> ForwardIndex.InputKeyIterator<Key, Value> getInstance() {
    //noinspection unchecked
    return EMPTY_INPUT_KEY_ITERATOR;
  }

  @Override
  public boolean isAssociatedValueEqual(@Nullable Value value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasNext() {
    return false;
  }

  @Override
  public Key next() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}

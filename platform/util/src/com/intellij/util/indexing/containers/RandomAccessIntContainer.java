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
package com.intellij.util.indexing.containers;

import org.jetbrains.annotations.NotNull;

/**
 * Represents random access container of int-s, namely indexed input ids.
 */
public interface RandomAccessIntContainer {
  Object clone();
  boolean add(int value);
  boolean remove(int value);

  @NotNull
  IntIdsIterator intIterator();

  void compact();
  int size();
  boolean contains(int value);

  @NotNull
  RandomAccessIntContainer ensureContainerCapacity(int diff);
}

/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @deprecated Use {@link Collections#emptyIterator()} instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public class EmptyIterator<T> implements Iterator<T> {
  private static final EmptyIterator INSTANCE = new EmptyIterator();
  public static <T> EmptyIterator<T> getInstance() {
    //noinspection unchecked
    return INSTANCE;
  }
  public boolean hasNext() {
    return false;
  }

  public T next() {
    throw new NoSuchElementException();
  }

  public void remove() {
    throw new IllegalStateException();
  }
}

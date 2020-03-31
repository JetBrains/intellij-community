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

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

class SequenceIterator<T> implements Iterator<T> {
  private final Iterator<T>[] myIterators;
  private int myCurrentIndex;

  @SafeVarargs
  SequenceIterator(Iterator<? extends T> @NotNull ... iterators){
    //noinspection unchecked
    myIterators = new Iterator[iterators.length];
    System.arraycopy(iterators, 0, myIterators, 0, iterators.length);
  }
  SequenceIterator(@NotNull Collection<? extends Iterator<? extends T>> iterators) {
    //noinspection unchecked
    this(iterators.toArray(new Iterator[0]));
  }

  @Override
  public boolean hasNext(){
    for (int index = myCurrentIndex; index < myIterators.length; index++) {
      Iterator<T> iterator = myIterators[index];
      if (iterator != null && iterator.hasNext()) {
        myCurrentIndex = index;
        return true;
      }
    }
    return false;
  }

  @Override
  public T next(){
    if(hasNext()) {
      return myIterators[myCurrentIndex].next();
    }
    throw new NoSuchElementException("Iterator has no more elements");
  }

  @Override
  public void remove(){
    if(myCurrentIndex >= myIterators.length){
      throw new IllegalStateException();
    }
    myIterators[myCurrentIndex].remove();
  }

  @NotNull
  public static <T> SequenceIterator<T> create(@NotNull Iterator<T> first, @NotNull Iterator<T> second) {
    return new SequenceIterator<>(first, second);
  }

  @NotNull
  public static <T> SequenceIterator<T> create(@NotNull Iterator<T> first, @NotNull Iterator<T> second, @NotNull Iterator<T> third) {
    return new SequenceIterator<>(first, second, third);
  }
}


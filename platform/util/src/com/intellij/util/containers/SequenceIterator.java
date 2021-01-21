// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

final class SequenceIterator<T> implements Iterator<T> {
  private final Iterator<? extends T>[] myIterators;
  private int myCurrentIndex;

  @SafeVarargs
  SequenceIterator(Iterator<? extends T> @NotNull ... iterators) {
    myIterators = iterators.clone();
  }

  SequenceIterator(@NotNull Collection<? extends Iterator<? extends T>> iterators) {
    //noinspection unchecked
    this(iterators.toArray(new Iterator[0]));
  }

  @Override
  public boolean hasNext(){
    for (int index = myCurrentIndex; index < myIterators.length; index++) {
      Iterator<? extends T> iterator = myIterators[index];
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
  public static <T> SequenceIterator<T> create(@NotNull Iterator<? extends T> first, @NotNull Iterator<? extends T> second) {
    return new SequenceIterator<>(first, second);
  }

  @NotNull
  public static <T> SequenceIterator<T> create(@NotNull Iterator<? extends T> first, @NotNull Iterator<? extends T> second, @NotNull Iterator<? extends T> third) {
    return new SequenceIterator<>(first, second, third);
  }
}


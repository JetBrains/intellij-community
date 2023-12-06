// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ArrayListSet<E> extends AbstractSet<E> {
  private final List<E> myList = new ArrayList<>();

  @Override
  public @NotNull Iterator<E> iterator() {
    return myList.iterator();
  }

  @Override
  public int size() {
    return myList.size();
  }

  @Override
  public boolean contains(Object object) {
    return myList.contains(object);
  }

  @Override
  public boolean add(E e) {
    if (!myList.contains(e)){
      myList.add(e);
      return true;
    }
    else{
      return false;
    }
  }

  @Override
  public boolean remove(Object object) {
    return myList.remove(object);
  }

  @Override
  public void clear() {
    myList.clear();
  }

  public E get(int index) {
    return myList.get(index);
  }
}

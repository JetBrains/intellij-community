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

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ArrayListSet<E> extends AbstractSet<E> {
  private final List<E> myList = new ArrayList<E>();

  @NotNull
  @Override
  public Iterator<E> iterator() {
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
}

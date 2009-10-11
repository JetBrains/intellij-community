/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util;

import java.util.ArrayList;
import java.util.Collection;

public class ListWithSelection<E> extends ArrayList<E> {
  private E mySelection;

  public ListWithSelection(Collection<E> collection) {
    super(collection);
  }

  public ListWithSelection(Collection<E> collection, E selection) {
    this(collection);
    select(selection);
  }

  public ListWithSelection() {
    this(new ArrayList<E>());
  }

  public boolean select(E object){
    if (!contains(object))
      return false;
    mySelection = object;
    return true;
  }

  public E getSelection(){
    return mySelection;
  }

  public void selectFirst() {
    select(get(0));
  }

  public Integer getSelectedIndex() {
    return new Integer(indexOf(mySelection));
  }
}

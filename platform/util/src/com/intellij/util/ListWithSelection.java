// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import java.util.ArrayList;
import java.util.Collection;

public class ListWithSelection<E> extends ArrayList<E> {
  private E mySelection;

  public ListWithSelection(Collection<? extends E> collection) {
    super(collection);
  }

  public ListWithSelection(Collection<? extends E> collection, E selection) {
    this(collection);
    select(selection);
  }

  public ListWithSelection() {
    this(new ArrayList<>());
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
    return Integer.valueOf(indexOf(mySelection));
  }
}

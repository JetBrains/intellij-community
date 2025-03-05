// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class TreeItem <Data> {
  private Data myData;
  private TreeItem<Data> myParent;
  private final List<TreeItem<Data>> myChildren = new ArrayList<>();

  public TreeItem(Data data) {
    myData = data;
  }

  public Data getData() {
    return myData;
  }

  public void setData(Data data) {
    myData = data;
  }

  public TreeItem<Data> getParent() {
    return myParent;
  }

  @Contract(pure = true)
  public @NotNull List<TreeItem<Data>> getChildren() {
    return myChildren;
  }

  private void setParent(TreeItem<Data> parent) {
    myParent = parent;
  }

  public void addChild(TreeItem<Data> child) {
    child.setParent(this);
    myChildren.add(child);
  }

  public void addChildAfter(TreeItem<Data> child, TreeItem<Data> after) {
    child.setParent(this);
    int idx = -1;
    for (int i = 0; i < myChildren.size(); i++) {
      TreeItem<Data> item = myChildren.get(i);
      if (item.equals(after)) {
        idx = i;
        break;
      }
    }
    if (idx == -1) {
      myChildren.add(child);
    } else {
      myChildren.add(idx, child);
    }
  }
}

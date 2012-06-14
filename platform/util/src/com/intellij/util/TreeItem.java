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
import java.util.List;

public class TreeItem <Data> {
  private Data myData;
  private TreeItem<Data> myParent;
  private final List<TreeItem<Data>> myChildren = new ArrayList<TreeItem<Data>>();

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

  public List<TreeItem<Data>> getChildren() {
    return myChildren;
  }

  protected void setParent(TreeItem<Data> parent) {
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

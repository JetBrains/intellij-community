// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

// stores result of various FSRecords.list*() methods and the current FSRecords.localModCount for optimistic locking support
class ListResult<T extends ChildInfo> {
  private final int modStamp;
  final List<? extends T> children;

  ListResult(@NotNull List<? extends T> children) {
    this(FSRecords.getLocalModCount(), children);
  }
  private ListResult(int modStamp, @NotNull List<? extends T> children) {
    this.modStamp = modStamp;
    this.children = children;
  }

  @Override
  public String toString() {
    return "modStamp: " + modStamp + "; children: " + children;
  }

  @Contract(pure=true)
  @NotNull
  ListResult<T> insert(@NotNull T child) {
    List<T> newChildren = new ArrayList<>(children.size() + 1);
    int id = child.getId();
    int i = ObjectUtils.binarySearch(0, children.size(), mid -> Integer.compare(children.get(mid).getId(), id));
    if (i >= 0) {
      // wow, child already there, replace array with the new child
      newChildren.addAll(children);
      newChildren.set(i, child);
    }
    else {
      int toInsert = -i - 1;
      for (int j=0; j<toInsert; j++) {
        newChildren.add(children.get(j));
      }
      newChildren.add(child);
      for (int j=toInsert; j<children.size(); j++) {
        newChildren.add(children.get(j));
      }
    }
    return new ListResult<>(modStamp, newChildren);
  }

  @Contract(pure=true)
  @NotNull
  ListResult<T> remove(@NotNull T child) {
    List<T> newChildren = new ArrayList<>(children.size() + 1);
    int id = child.getId();
    int toRemove = ObjectUtils.binarySearch(0, children.size(), mid -> Integer.compare(children.get(mid).getId(), id));
    if (toRemove < 0) {
      // wow, child is not there
      return this;
    }
    else {
      for (int j = 0; j < toRemove; j++) {
        newChildren.add(children.get(j));
      }
      for (int j = toRemove + 1; j < children.size(); j++) {
        newChildren.add(children.get(j));
      }
    }
    return new ListResult<>(modStamp, newChildren);
  }

  @Contract(pure=true)
  @NotNull
  <U extends ChildInfo> ListResult<U> merge(@NotNull List<? extends U> list) {
    // assume list is sorted
    List<U> newChildren = new ArrayList<>(children.size() + list.size());
    ContainerUtil.processSortedListsInOrder(children, list, ChildInfo.BY_ID, true, out->newChildren.add((U)out));
    return new ListResult<>(modStamp, newChildren);
  }

  @Contract(pure=true)
  @NotNull
  ListResult<T> subtract(@NotNull List<? extends T> list) {
    // assume list is sorted
    List<T> newChildren = new ArrayList<>(children.size() + list.size());
    int index1 = 0;
    int index2 = 0;
    while (index1 < children.size() && index2 < list.size()) {
      T element1 = children.get(index1);
      T element2 = list.get(index2);
      int c = ChildInfo.BY_ID.compare(element1, element2);
      if (c == 0) {
        index1++;
        index2++;
      }
      else if (c < 0) {
        newChildren.add(element1);
        index1++;
      }
      else {
        // wow, the element not in the children
        index2++;
      }
    }
    for (int i=index1; i<children.size(); i++) {
      newChildren.add(children.get(i));
    }
    return new ListResult<>(modStamp, newChildren);
  }

  boolean childrenWereChangedSinceLastList() {
    return modStamp != FSRecords.getLocalModCount();
  }
}

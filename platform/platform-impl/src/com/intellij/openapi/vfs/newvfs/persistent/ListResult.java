// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.util.ObjectUtils;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

// stores result of various FSRecords.list*() methods and the current FSRecords.localModCount for optimistic locking support
class ListResult {
  private final int modStamp;
  // sorted by getId()
  final List<? extends ChildInfo> children;

  ListResult(@NotNull List<? extends ChildInfo> children) {
    this(FSRecords.getLocalModCount(), children);
  }
  private ListResult(int modStamp, @NotNull List<? extends ChildInfo> children) {
    this.modStamp = modStamp;
    this.children = children;
  }

  @Override
  public String toString() {
    return "modStamp: " + modStamp + "; children: " + children;
  }

  @Contract(pure=true)
  @NotNull
  ListResult insert(@NotNull ChildInfo child) {
    List<ChildInfo> newChildren = new ArrayList<>(children.size() + 1);
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
    return new ListResult(modStamp, newChildren);
  }

  @Contract(pure=true)
  @NotNull
  ListResult remove(@NotNull ChildInfo child) {
    List<ChildInfo> newChildren = new ArrayList<>(children.size() + 1);
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
    return new ListResult(modStamp, newChildren);
  }

  @Contract(pure=true)
  @NotNull
  ListResult merge(@NotNull List<? extends ChildInfo> newList, @NotNull TObjectHashingStrategy<? super CharSequence> hashingStrategy) {
    // assume list is sorted
    ListResult newChildren = FSRecords.mergeByName(this, new ListResult(newList), hashingStrategy);
    return new ListResult(modStamp, newChildren.children);
  }

  @Contract(pure=true)
  @NotNull
  ListResult subtract(@NotNull List<? extends ChildInfo> list) {
    // assume list is sorted
    List<ChildInfo> newChildren = new ArrayList<>(children.size() + list.size());
    int index1 = 0;
    int index2 = 0;
    while (index1 < children.size() && index2 < list.size()) {
      ChildInfo element1 = children.get(index1);
      ChildInfo element2 = list.get(index2);
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
    return new ListResult(modStamp, newChildren);
  }

  boolean childrenWereChangedSinceLastList() {
    return modStamp != FSRecords.getLocalModCount();
  }
}

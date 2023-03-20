// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.FastUtilHashingStrategies;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Stores result of various `FSRecords#list*` methods and the current `FSRecords#getModCount` for optimistic locking support.
final class ListResult {
  private final int modStamp;
  final List<? extends ChildInfo> children;  // sorted by `#getId`
  private final int myParentId;

  ListResult(@NotNull List<? extends ChildInfo> children, int parentId) {
    this(FSRecords.getModCount(parentId), children, parentId);
  }

  private ListResult(int modStamp, @NotNull List<? extends ChildInfo> children, int parentId) {
    this.modStamp = modStamp;
    this.children = children;
    myParentId = parentId;
    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() && !ApplicationManagerEx.isInStressTest() || app.isInternal()) {
      assertSortedById(children);
    }
  }

  private void assertSortedById(@NotNull List<? extends ChildInfo> children) {
    for (int i = 1; i < children.size(); i++) {
      ChildInfo info = children.get(i);
      if (info.getId() < children.get(i - 1).getId()) {
        throw new IllegalArgumentException("Unsorted list: " + this);
      }
    }
  }

  @Contract(pure=true)
  @NotNull ListResult insert(@NotNull ChildInfo child) {
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
    return new ListResult(modStamp, newChildren, myParentId);
  }

  @Contract(pure=true)
  @NotNull ListResult remove(@NotNull ChildInfo child) {
    List<ChildInfo> newChildren = new ArrayList<>(children.size() + 1);
    int id = child.getId();
    int toRemove = ObjectUtils.binarySearch(0, children.size(), mid -> Integer.compare(children.get(mid).getId(), id));
    if (toRemove < 0) {
      // wow, the child is not there
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
    return new ListResult(modStamp, newChildren, myParentId);
  }

  // Returns entries from this list plus `otherList';
  // in case of a name clash uses ID from the corresponding this list entry and a name from the `otherList` entry
  // (to avoid duplicating ids: preserve old id but supply new name).
  @Contract(pure=true)
  @NotNull ListResult merge(@NotNull List<? extends ChildInfo> newChildren, boolean isCaseSensitive) {
    ListResult newList = new ListResult(newChildren, myParentId);  // assume the list is sorted
    if (children.isEmpty()) return newList;
    List<? extends ChildInfo> oldChildren = children;
    // Both `newChildren` and `oldChildren` are sorted by id, but not `nameId`, so plain O(N) merging is not possible.
    // Instead, try to eliminate entries with the same id from both lists first (since they have the same `nameId`),
    // and compare the rest by (slower) `nameId`.
    // Typically, when `newChildren` contains 5K entries + a couple absent from `oldChildren`, and `oldChildren` contains 5K + a couple entries,
    // these maps will contain just a couple of entries absent from each other, not thousands.

    int size = Math.max(oldChildren.size(), newChildren.size());
    Object2IntMap<CharSequence> nameToIndex = new Object2IntOpenCustomHashMap<>(size, FastUtilHashingStrategies.getCharSequenceStrategy(isCaseSensitive));
    // distinguish between absence and the 0th index
    nameToIndex.defaultReturnValue(-1);
    boolean needToSortResult = false;
    List<ChildInfo> result = new ArrayList<>(size);
    for (int i = 0, j = 0; i < newChildren.size() || j < oldChildren.size(); ) {
      ChildInfo newChild = i == newChildren.size() ? null : newChildren.get(i);
      ChildInfo oldChild = j == oldChildren.size() ? null : oldChildren.get(j);
      int newId = newChild == null ? Integer.MAX_VALUE : newChild.getId();
      int oldId = oldChild == null ? Integer.MAX_VALUE : oldChild.getId();
      if (newId == oldId) {
        i++;
        j++;
        result.add(oldChild);
      }
      else if (newId < oldId) {
        // `newId` is absent from `oldChildren`
        CharSequence name = newChild.getName();
        int dupI = nameToIndex.put(name, result.size());
        if (dupI == -1) {
          result.add(newChild);
        }
        else {
          // aha, found entry in `result` with the same name.
          // That previous entry must come from the `oldChildren`
          // so replace just the name (the new name must have changed its case), leave id the same
          ChildInfo oldDup = result.get(dupI);
          int nameId = newChild.getNameId();
          assert nameId > 0 : newChildren;
          ChildInfo replaced = ((ChildInfoImpl)oldDup).withNameId(nameId);
          result.set(dupI, replaced);
          needToSortResult = true;
        }
        i++;
      }
      else {
        // oldId is absent from `newChildren`
        CharSequence name = oldChild.getName();
        int dupI = nameToIndex.put(name, result.size());
        if (dupI == -1) {
          result.add(oldChild);
        }
        else {
          // Aha, found an entry in `result` with the same name.
          // That previous entry must come from the `newChildren`,
          // so leave the new name (which must have changed its case) and replace the ID.
          ChildInfo dup = result.get(dupI);
          int nameId = dup.getNameId();
          assert nameId > 0 : this;
          ChildInfo replaced = ((ChildInfoImpl)dup).withId(oldChild.getId());
          result.set(dupI, replaced);
          needToSortResult = true;
        }
        j++;
      }
    }
    if (needToSortResult) {
      result.sort(ChildInfo.BY_ID);
    }
    List<? extends ChildInfo> newRes = nameToIndex.isEmpty() ? newChildren : result;
    return new ListResult(modStamp, newRes, myParentId);
  }

  @Contract(pure=true)
  @NotNull ListResult subtract(@NotNull List<? extends ChildInfo> list) {
    List<ChildInfo> newChildren = new ArrayList<>(children.size() + list.size());  // assume the list is sorted
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
        // Wow, the element is not among the children.
        index2++;
      }
    }
    for (int i=index1; i<children.size(); i++) {
      newChildren.add(children.get(i));
    }
    return new ListResult(modStamp, newChildren, myParentId);
  }

  boolean childrenWereChangedSinceLastList() {
    return modStamp != FSRecords.getModCount(myParentId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ListResult result = (ListResult)o;
    return modStamp == result.modStamp && children.equals(result.children);
  }

  @Override
  public int hashCode() {
    return Objects.hash(modStamp, children);
  }

  @Override
  public String toString() {
    return "modStamp: " + modStamp + "; children: " + children;
  }
}

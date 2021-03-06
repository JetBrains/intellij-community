// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.FastUtilHashingStrategies;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// stores result of various FSRecords.list*() methods and the current FSRecords.localModCount for optimistic locking support
final class ListResult {
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
  @NonNls
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

  // return entries from this list plus `otherList',
  // in case of name clash use id from the corresponding this list entry and name from the `otherList` entry (to avoid duplicating ids: preserve old id but supply new name)
  @Contract(pure=true)
  @NotNull
  ListResult merge(@NotNull List<? extends ChildInfo> otherList, boolean isCaseSensitive) {
    ListResult newList = new ListResult(otherList);
    if (children.isEmpty()) return newList;
    // assume list is sorted
    List<? extends ChildInfo> newChildren = newList.children;
    List<? extends ChildInfo> oldChildren = children;
    // both `newChildren` and `oldChildren` are sorted by id, but not nameId, so plain O(N) merge is not possible.
    // instead, try to eliminate entries with the same id from both lists first (since they have same nameId), and compare the rest by (slower) nameId.
    // typically, when `newChildren` contains 5K entries + couple absent from `oldChildren`, and `oldChildren` contains 5K+couple entries, these maps will contain a couple of entries absent from each other

    // name -> index in result
    Object2IntMap<CharSequence> nameToIndex =
      new Object2IntOpenCustomHashMap<>(Math.max(oldChildren.size(), newChildren.size()), FastUtilHashingStrategies
      .getCharSequenceStrategy(isCaseSensitive));
    // distinguish between absence and the 0th index
    nameToIndex.defaultReturnValue(-1);

    List<ChildInfo> result = new ArrayList<>(Math.max(oldChildren.size(), newChildren.size()));
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
        // newId is absent from `oldChildren`
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
          assert nameId > 0 : newList;
          ChildInfo replaced = ((ChildInfoImpl)oldDup).withNameId(nameId);
          result.set(dupI, replaced);
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
          // aha, found entry in `result` with the same name.
          // That previous entry must come from the `newChildren`
          // so leave the new name (the new name must have changed its case), replace the id
          ChildInfo dup = result.get(dupI);
          int nameId = dup.getNameId();
          assert nameId > 0 : this;
          ChildInfo replaced = ((ChildInfoImpl)dup).withId(oldChild.getId());
          result.set(dupI, replaced);
        }
        j++;
      }
    }
    ListResult newRes = nameToIndex.isEmpty() ? newList : new ListResult(result);
    return new ListResult(modStamp, newRes.children);
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
}

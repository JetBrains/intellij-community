// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;

import java.util.Iterator;
import java.util.List;

public class ChangeCounter implements ChangeList.Listener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.incrementalMerge.ChangeCounter");
  private static final Key<ChangeCounter> ourKey = Key.create("ChangeCounter");
  private final MergeList myMergeList;
  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private int myChangeCounter = 0;
  private int myConflictCounter = 0;

  private ChangeCounter(MergeList mergeList) {
    myMergeList = mergeList;
    myMergeList.addListener(this);
    updateCounters();
  }

  @Override
  public void onChangeApplied(ChangeList source) {
    updateCounters();
  }

  @Override
  public void onChangeRemoved(ChangeList source) {
    updateCounters();
  }

  public void addListener(Listener listener) { myListeners.add(listener); }
  public void removeListener(Listener listener) { myListeners.remove(listener); }

  private void updateCounters() {
    int conflictCounter = 0;
    int changeCounter = 0;
    Iterator<Change> allChanges = myMergeList.getAllChanges();
    while (allChanges.hasNext()) {
      Change change = allChanges.next();
      if (MergeList.NOT_CONFLICTS.value(change)) changeCounter++;
      else conflictCounter++;
    }
    if (myChangeCounter != changeCounter || myConflictCounter != conflictCounter) {
      myChangeCounter = changeCounter;
      myConflictCounter = conflictCounter;
      fireCountersChanged();
    }
  }

  private void fireCountersChanged() {
    for (Listener listener : myListeners) {
      listener.onCountersChanged(this);
    }
  }

  public int getChangeCounter() {
    return myChangeCounter;
  }

  public int getConflictCounter() {
    return myConflictCounter;
  }

  public static ChangeCounter getOrCreate(MergeList mergeList) {
    ChangeCounter data = mergeList.getUserData(ourKey);
    if (data == null) {
      data = new ChangeCounter(mergeList);
      mergeList.putUserData(ourKey, data);
    }
    return data;
  }

  public interface Listener {
    void onCountersChanged(ChangeCounter counter);
  }
}

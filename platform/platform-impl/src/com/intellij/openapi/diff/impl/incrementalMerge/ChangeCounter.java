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

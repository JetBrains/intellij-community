/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class VcsAbstractHistorySession implements VcsHistorySession {
  private final List<VcsFileRevision> myRevisions;
  private final Object myLock;
  private VcsRevisionNumber myCachedRevisionNumber;

  protected VcsRevisionNumber getCachedRevision() {
    synchronized (myLock) {
      return myCachedRevisionNumber;
    }
  }

  protected void setCachedRevision(final VcsRevisionNumber number) {
    synchronized (myLock) {
      myCachedRevisionNumber = number;
    }
  }

  public VcsAbstractHistorySession(List<? extends VcsFileRevision> revisions) {
    myLock = new Object();
    myRevisions = new ArrayList<>(revisions);
    myCachedRevisionNumber = calcCurrentRevisionNumber();
  }

  protected VcsAbstractHistorySession(List<? extends VcsFileRevision> revisions, VcsRevisionNumber currentRevisionNumber) {
    myLock = new Object();
    myRevisions = new ArrayList<>(revisions);
    myCachedRevisionNumber = currentRevisionNumber;
  }

  public List<VcsFileRevision> getRevisionList() {
    return myRevisions;
  }

  public void appendRevision(final VcsFileRevision revision) {
    myRevisions.add(revision);
  }

  /**
   * This method should return actual value for current revision (it can be changed after submit for example)
   * @return current file revision, null if file does not exist anymore
   */

  @Nullable
  protected abstract VcsRevisionNumber calcCurrentRevisionNumber();

  public final VcsRevisionNumber getCurrentRevisionNumber() {
    return getCachedRevision();
  }

  public boolean isCurrentRevision(VcsRevisionNumber rev) {
    VcsRevisionNumber revNumber = getCurrentRevisionNumber();
    return revNumber != null && revNumber.compareTo(rev) == 0;
  }

  public synchronized boolean shouldBeRefreshed() {
    final VcsRevisionNumber oldValue = getCachedRevision();
    final VcsRevisionNumber newNumber = calcCurrentRevisionNumber();
    setCachedRevision(newNumber);
    return !Comparing.equal(oldValue, newNumber);
  }

  public boolean isContentAvailable(VcsFileRevision revision) {
    return true;
  }

  public abstract VcsHistorySession copy();

  public VcsHistorySession copyWithCachedRevision() {
    final VcsAbstractHistorySession copy = (VcsAbstractHistorySession)copy();
    copy.setCachedRevision(myCachedRevisionNumber);
    return copy;
  }

  public Map<VcsRevisionNumber, VcsFileRevision> getHistoryAsMap() {
    final Map<VcsRevisionNumber, VcsFileRevision> map = new HashMap<>();
    for (VcsFileRevision revision : myRevisions) {
      map.put(revision.getRevisionNumber(), revision);
    }
    return map;
  }

  @Override
  public boolean hasLocalSource() {
    return true;
  }
}

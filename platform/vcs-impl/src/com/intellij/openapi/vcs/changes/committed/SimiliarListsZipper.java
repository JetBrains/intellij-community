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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import java.util.*;

public class SimiliarListsZipper {
  private final LinkedList<SubSequence<CommittedChangeList>> myLists;
  private final List<CommittedChangeList> myResult;
  private final VcsCommittedListsZipper myZipper;
  private final RepositoryLocationGroup myGroup;

  /**
   * 
   * @param lists - MUST be internally ordered in ??? ascending order
   * @param zipper
   */
  public SimiliarListsZipper(final Collection<List<CommittedChangeList>> lists,
                             final VcsCommittedListsZipper zipper, final RepositoryLocationGroup group) {
    myZipper = zipper;
    myGroup = group;
    myLists = new LinkedList<SubSequence<CommittedChangeList>>();
    myResult = new ArrayList<CommittedChangeList>();
    
    for (List<CommittedChangeList> list : lists) {
      if (! list.isEmpty()) {
        myLists.add(new SubSequence<CommittedChangeList>(list));
      }
    }
  }

  public void zip() {
    Collections.sort(myLists);
    
    while (! myLists.isEmpty()) {
      // check for equality of first n things
      if (tryZipFirstN()) {
        continue;
      }
      if (! usualStep()) {
        break;
      }
    }
  }

  private boolean usualStep() {
    while (! myLists.isEmpty()) {
      final SubSequence<CommittedChangeList> sequence = myLists.removeFirst();
      myResult.add(sequence.getCurrentList());

      if (sequence.hasNext()) {
        sequence.next();
        insert(sequence);
        return true;
      }
    }
    return false;
  }
  
  private boolean tryZipFirstN() {
    final SubSequence<CommittedChangeList> firstSequence = myLists.getFirst();
    List<SubSequence<CommittedChangeList>> removed = null;
    List<CommittedChangeList> toBeZipped = null;
    
    for (ListIterator<SubSequence<CommittedChangeList>> iterator = myLists.listIterator(1); iterator.hasNext();) {
      final SubSequence<CommittedChangeList> sequence = iterator.next();
      if (sequence.compareTo(firstSequence) != 0) {
        break;
      }
      if (removed == null) {
        removed = new ArrayList<SubSequence<CommittedChangeList>>();
        toBeZipped = new ArrayList<CommittedChangeList>();
      }
      iterator.remove();
      removed.add(sequence);
      toBeZipped.add(sequence.getCurrentList());
    }
    if (removed != null) {
      // also remove first
      myLists.removeFirst();
      // and add to equal collection
      removed.add(firstSequence);
      toBeZipped.add(firstSequence.getCurrentList());

      final CommittedChangeList zippedList = myZipper.zip(myGroup, toBeZipped);
      myResult.add(zippedList);

      for (SubSequence<CommittedChangeList> sequence : removed) {
        if (sequence.hasNext()) {
          sequence.next();
          insert(sequence);
        }
      }
      return true;
    }
    return false;
  }

  private void insert(final SubSequence<CommittedChangeList> firstSequence) {
    for (ListIterator<SubSequence<CommittedChangeList>> iterator = myLists.listIterator(); iterator.hasNext();) {
      final SubSequence<CommittedChangeList> currentSequence = iterator.next();
      if (currentSequence.compareTo(firstSequence) > 0) {
        iterator.previous();
        iterator.add(firstSequence);
        return;
      }
    }
    myLists.addLast(firstSequence);
  }

  public List<CommittedChangeList> getResult() {
    return myResult;
  }

  private class SubSequence<T extends CommittedChangeList> implements Comparable<SubSequence<T>> {
    private int myIdx;
    private long myCachedNumber;
    private final List<T> myList;

    private SubSequence(final List<T> list) {
      myList = list;
      myIdx = 0;
      myCachedNumber = myZipper.getNumber(myList.get(0));
    }

    boolean hasNext() {
      return myIdx < (myList.size() - 1);
    }

    public int compareTo(final SubSequence<T> other) {
      final long sign = myCachedNumber - other.myCachedNumber;
      return sign == 0 ? 0 : ((sign < 0) ? -1 : 1);
    }

    void next() {
      if (hasNext()) {
        ++ myIdx;
        myCachedNumber = myZipper.getNumber(myList.get(myIdx));
      }
    }

    T getCurrentList() {
      return myList.get(myIdx);
    }
  }
}

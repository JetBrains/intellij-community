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

import com.google.common.collect.Iterables;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class SimiliarListsZipper {

  private final Collection<List<CommittedChangeList>> myLists;
  private final Comparator<CommittedChangeList> myComparator;
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
    myLists = lists;
    myResult = new ArrayList<CommittedChangeList>();
    myComparator = new Comparator<CommittedChangeList>() {
      @Override
      public int compare(CommittedChangeList o1, CommittedChangeList o2) {
        return Comparing.compare(zipper.getNumber(o1), zipper.getNumber(o2));
      }
    };
  }

  public void zip() {
    List<CommittedChangeList> equalLists = new ArrayList<CommittedChangeList>();
    CommittedChangeList previousList = null;

    for (CommittedChangeList list : Iterables.mergeSorted(myLists, myComparator)) {
      if (previousList != null && myComparator.compare(previousList, list) != 0) {
        myResult.add(zip(equalLists));
        equalLists.clear();
      }
      equalLists.add(list);
      previousList = list;
    }
    if (!equalLists.isEmpty()) {
      myResult.add(zip(equalLists));
    }
  }

  @NotNull
  private CommittedChangeList zip(@NotNull List<CommittedChangeList> equalLists) {
    if (equalLists.isEmpty()) {
      throw new IllegalArgumentException("equalLists can not be empty");
    }

    return equalLists.size() > 1 ? myZipper.zip(myGroup, equalLists) : equalLists.get(0);
  }

  public List<CommittedChangeList> getResult() {
    return myResult;
  }
}

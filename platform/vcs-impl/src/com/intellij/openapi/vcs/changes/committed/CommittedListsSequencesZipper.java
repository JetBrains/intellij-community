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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class CommittedListsSequencesZipper {

  @NotNull private final VcsCommittedListsZipper myVcsPartner;
  @NotNull private final List<RepositoryLocation> myInLocations;
  @NotNull private final Map<String, List<CommittedChangeList>> myInLists;
  @NotNull private final Comparator<CommittedChangeList> myComparator;

  public CommittedListsSequencesZipper(@NotNull VcsCommittedListsZipper vcsPartner) {
    myVcsPartner = vcsPartner;
    myInLocations = ContainerUtil.newArrayList();
    myInLists = ContainerUtil.newHashMap();
    myComparator = new Comparator<CommittedChangeList>() {
      public int compare(final CommittedChangeList o1, final CommittedChangeList o2) {
        return Comparing.compare(myVcsPartner.getNumber(o1), myVcsPartner.getNumber(o2));
      }
    };
  }

  public void add(@NotNull RepositoryLocation location, @NotNull List<CommittedChangeList> lists) {
    myInLocations.add(location);
    Collections.sort(lists, myComparator);
    myInLists.put(location.toPresentableString(), lists);
  }

  @NotNull
  public List<CommittedChangeList> execute() {
    Pair<List<RepositoryLocationGroup>, List<RepositoryLocation>> groupingResult = myVcsPartner.groupLocations(myInLocations);
    List<CommittedChangeList> result = ContainerUtil.newArrayList();

    result.addAll(ContainerUtil.flatten(collectChangeLists(groupingResult.getSecond())));
    for (RepositoryLocationGroup group : groupingResult.getFirst()) {
      result.addAll(mergeLocationGroupChangeLists(group));
    }

    return result;
  }

  @NotNull
  private List<List<CommittedChangeList>> collectChangeLists(@NotNull List<RepositoryLocation> locations) {
    List<List<CommittedChangeList>> result = ContainerUtil.newArrayListWithCapacity(locations.size());

    for (RepositoryLocation location : locations) {
      result.add(myInLists.get(location.toPresentableString()));
    }

    return result;
  }

  @NotNull
  private List<CommittedChangeList> mergeLocationGroupChangeLists(@NotNull RepositoryLocationGroup group) {
    List<CommittedChangeList> result = ContainerUtil.newArrayList();
    List<CommittedChangeList> equalLists = ContainerUtil.newArrayList();
    CommittedChangeList previousList = null;

    for (CommittedChangeList list : Iterables.mergeSorted(collectChangeLists(group.getLocations()), myComparator)) {
      if (previousList != null && myComparator.compare(previousList, list) != 0) {
        result.add(zip(group, equalLists));
        equalLists.clear();
      }
      equalLists.add(list);
      previousList = list;
    }
    if (!equalLists.isEmpty()) {
      result.add(zip(group, equalLists));
    }

    return result;
  }

  @NotNull
  private CommittedChangeList zip(@NotNull RepositoryLocationGroup group, @NotNull List<CommittedChangeList> equalLists) {
    if (equalLists.isEmpty()) {
      throw new IllegalArgumentException("equalLists can not be empty");
    }

    return equalLists.size() > 1 ? myVcsPartner.zip(group, equalLists) : equalLists.get(0);
  }
}

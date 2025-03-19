// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.committed;

import com.google.common.collect.Iterables;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
public class CommittedListsSequencesZipper {

  private final @NotNull VcsCommittedListsZipper myVcsPartner;
  private final @NotNull List<RepositoryLocation> myInLocations;
  private final @NotNull Map<String, List<? extends CommittedChangeList>> myInLists;
  private final @NotNull Comparator<CommittedChangeList> myComparator;

  public CommittedListsSequencesZipper(@NotNull VcsCommittedListsZipper vcsPartner) {
    myVcsPartner = vcsPartner;
    myInLocations = new ArrayList<>();
    myInLists = new HashMap<>();
    myComparator = (o1, o2) -> {
      return Long.compare(myVcsPartner.getNumber(o1), myVcsPartner.getNumber(o2));
    };
  }

  @Contract(mutates = "this,param2")
  public void add(@NotNull RepositoryLocation location, @NotNull List<? extends CommittedChangeList> lists) {
    myInLocations.add(location);
    lists.sort(myComparator);
    myInLists.put(location.toPresentableString(), lists);
  }

  public @NotNull List<CommittedChangeList> execute() {
    Pair<List<RepositoryLocationGroup>, List<RepositoryLocation>> groupingResult = myVcsPartner.groupLocations(myInLocations);
    List<CommittedChangeList> result = new ArrayList<>();

    result.addAll(ContainerUtil.flatten(collectChangeLists(groupingResult.getSecond())));
    for (RepositoryLocationGroup group : groupingResult.getFirst()) {
      result.addAll(mergeLocationGroupChangeLists(group));
    }

    return result;
  }

  private @NotNull List<List<? extends CommittedChangeList>> collectChangeLists(@NotNull List<? extends RepositoryLocation> locations) {
    List<List<? extends CommittedChangeList>> result = new ArrayList<>(locations.size());

    for (RepositoryLocation location : locations) {
      result.add(myInLists.get(location.toPresentableString()));
    }

    return result;
  }

  private @NotNull List<CommittedChangeList> mergeLocationGroupChangeLists(@NotNull RepositoryLocationGroup group) {
    List<CommittedChangeList> result = new ArrayList<>();
    List<CommittedChangeList> equalLists = new ArrayList<>();
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

  private @NotNull CommittedChangeList zip(@NotNull RepositoryLocationGroup group, @NotNull List<? extends CommittedChangeList> equalLists) {
    if (equalLists.isEmpty()) {
      throw new IllegalArgumentException("equalLists can not be empty");
    }

    return equalLists.size() > 1 ? myVcsPartner.zip(group, equalLists) : equalLists.get(0);
  }
}

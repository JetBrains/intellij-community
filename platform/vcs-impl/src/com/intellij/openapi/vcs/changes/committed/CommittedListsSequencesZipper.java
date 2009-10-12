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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import java.util.*;

public class CommittedListsSequencesZipper {
  private final VcsCommittedListsZipper myVcsPartner;
  private final List<RepositoryLocation> myInLocations;
  private final Map<String, List<CommittedChangeList>> myInLists;

  public CommittedListsSequencesZipper(final VcsCommittedListsZipper vcsPartner) {
    myVcsPartner = vcsPartner;
    myInLocations = new ArrayList<RepositoryLocation>();
    myInLists = new HashMap<String, List<CommittedChangeList>>();
  }

  public void add(final RepositoryLocation location, final List<CommittedChangeList> lists) {
    myInLocations.add(location);
    Collections.sort(lists, new Comparator<CommittedChangeList>() {
      public int compare(final CommittedChangeList o1, final CommittedChangeList o2) {
        final long num1 = myVcsPartner.getNumber(o1);
        final long num2 = myVcsPartner.getNumber(o2);
        return num1 == num2 ? 0 : (num1 < num2) ? -1 : 1;
      }
    });
    myInLists.put(location.toPresentableString(), lists);
  }

  public List<CommittedChangeList> execute() {
    final Pair<List<RepositoryLocationGroup>,List<RepositoryLocation>> groupingResult = myVcsPartner.groupLocations(myInLocations);
    final List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();

    for (RepositoryLocation location : groupingResult.getSecond()) {
      result.addAll(myInLists.get(location.toPresentableString()));
    }

    for (RepositoryLocationGroup group : groupingResult.getFirst()) {
      final List<RepositoryLocation> locations = group.getLocations();
      final List<List<CommittedChangeList>> lists = new ArrayList<List<CommittedChangeList>>(locations.size());
      for (RepositoryLocation location : locations) {
        lists.add(myInLists.get(location.toPresentableString()));
      }
      final SimiliarListsZipper zipper = new SimiliarListsZipper(lists, myVcsPartner, group);
      zipper.zip();
      result.addAll(zipper.getResult());
    }
    return result;
  }
}

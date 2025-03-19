// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class VcsCommittedListsZipperAdapter implements VcsCommittedListsZipper {
  private final GroupCreator myGroupCreator;

  public interface GroupCreator {
    Object createKey(final RepositoryLocation location);
    RepositoryLocationGroup createGroup(final Object key, final Collection<RepositoryLocation> locations);
  }

  protected VcsCommittedListsZipperAdapter(final GroupCreator groupCreator) {
    myGroupCreator = groupCreator;
  }

  @Override
  public @NotNull Pair<List<RepositoryLocationGroup>, List<RepositoryLocation>> groupLocations(@NotNull List<? extends RepositoryLocation> in) {
    final List<RepositoryLocationGroup> groups = new ArrayList<>();
    final List<RepositoryLocation> singles = new ArrayList<>();

    final MultiMap<Object, RepositoryLocation> map = new MultiMap<>();

    for (RepositoryLocation location : in) {
      final Object key = myGroupCreator.createKey(location);
      map.putValue(key, location);
    }

    final Set<Object> keys = map.keySet();
    for (Object key : keys) {
      final Collection<RepositoryLocation> locations = map.get(key);
      if (locations.size() == 1) {
        singles.addAll(locations);
      } else {
        final RepositoryLocationGroup group = myGroupCreator.createGroup(key, locations);
        groups.add(group);
      }
    }

    return Pair.create(groups, singles);
  }

  @Override
  public @NotNull CommittedChangeList zip(@NotNull RepositoryLocationGroup group, @NotNull List<? extends CommittedChangeList> lists) {
    if (lists.size() == 1) {
      return lists.get(0);
    }
    final CommittedChangeList result = lists.get(0);

    Set<Change> processed = new HashSet<>(result.getChanges());

    for (int i = 1; i < lists.size(); i++) {
      for (Change change : lists.get(i).getChanges()) {
        if (!processed.add(change)) {
          result.getChanges().add(change);
        }
      }
    }
    return result;
  }

  @Override
  public long getNumber(@NotNull CommittedChangeList list) {
    return list.getNumber();
  }
}

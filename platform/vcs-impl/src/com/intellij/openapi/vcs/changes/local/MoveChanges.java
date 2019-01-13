// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class MoveChanges implements ChangeListCommand {
  private final String myName;
  private final List<Change> myChanges;

  private MultiMap<LocalChangeList, Change> myMovedFrom;
  private LocalChangeList myListCopy;

  public MoveChanges(@NotNull String name, @NotNull Change[] changes) {
    myName = name;
    myChanges = ContainerUtil.skipNulls(Arrays.asList(changes));
  }

  @Override
  public void apply(final ChangeListWorker worker) {
    myMovedFrom = worker.moveChangesTo(myName, myChanges);

    myListCopy = worker.getChangeListByName(myName);
  }

  @Override
  public void doNotify(final EventDispatcher<? extends ChangeListListener> dispatcher) {
    if (myMovedFrom != null && myListCopy != null) {
      for (LocalChangeList fromList : myMovedFrom.keySet()) {
        Collection<Change> changesInList = myMovedFrom.get(fromList);
        dispatcher.getMulticaster().changesMoved(changesInList, fromList, myListCopy);
      }
    }
  }
}

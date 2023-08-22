// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;

import java.util.List;

public class RemoveList implements ChangeListCommand {
  private final String myName;

  private LocalChangeList myListCopy;
  private LocalChangeList myDefaultListCopy;
  private List<Change> myMovedChanges;

  public RemoveList(final String name) {
    myName = name;
  }

  @Override
  public void apply(final ChangeListWorker worker) {
    myListCopy = worker.getChangeListByName(myName);

    myMovedChanges = worker.removeChangeList(myName);

    myDefaultListCopy = worker.getDefaultList();
  }

  @Override
  public void doNotify(final ChangeListListener listener) {
    if (myListCopy != null && myMovedChanges != null) {
      if (!myMovedChanges.isEmpty()) {
        listener.changesMoved(myMovedChanges, myListCopy, myDefaultListCopy);
      }
      listener.changeListRemoved(myListCopy);
    }
  }
}

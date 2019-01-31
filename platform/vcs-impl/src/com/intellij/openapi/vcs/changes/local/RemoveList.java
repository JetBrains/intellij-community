// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.EventDispatcher;

public class RemoveList implements ChangeListCommand {
  private final String myName;
  private boolean myRemoved;

  private LocalChangeList myListCopy;
  private LocalChangeList myDefaultListCopy;

  public RemoveList(final String name) {
    myName = name;
  }

  @Override
  public void apply(final ChangeListWorker worker) {
    myListCopy = worker.getChangeListByName(myName);
    myDefaultListCopy = worker.getDefaultList();
    myRemoved = worker.removeChangeList(myName);
  }

  @Override
  public void doNotify(final EventDispatcher<? extends ChangeListListener> dispatcher) {
    if (myListCopy != null && myRemoved ) {
      ChangeListListener multicaster = dispatcher.getMulticaster();
      multicaster.changesMoved(myListCopy.getChanges(), myListCopy, myDefaultListCopy);
      multicaster.changeListRemoved(myListCopy);
    }
  }
}

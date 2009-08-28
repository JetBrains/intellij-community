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

  public void apply(final ChangeListWorker worker) {
    myListCopy = worker.getCopyByName(myName);
    myDefaultListCopy = worker.getDefaultListCopy();
    myRemoved = worker.removeChangeList(myName);
  }

  public void doNotify(final EventDispatcher<ChangeListListener> dispatcher) {
    if (myRemoved) {
      final ChangeListListener multicaster = dispatcher.getMulticaster();
      multicaster.changesMoved(myListCopy.getChanges(), myListCopy, myDefaultListCopy);
      multicaster.changeListRemoved(myListCopy);
    }
  }

  public boolean isRemoved() {
    return myRemoved;
  }
}

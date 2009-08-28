package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.EventDispatcher;

public class AddList implements ChangeListCommand {
  private final String myName;
  private final String myComment;
  private LocalChangeList myNewListCopy;

  public AddList(final String name, final String comment) {
    myName = name;
    myComment = comment;
  }

  public void apply(final ChangeListWorker worker) {
    myNewListCopy = worker.addChangeList(myName, myComment);
  }

  public void doNotify(final EventDispatcher<ChangeListListener> dispatcher) {
    dispatcher.getMulticaster().changeListAdded(myNewListCopy);
  }

  public LocalChangeList getNewListCopy() {
    return myNewListCopy;
  }
}

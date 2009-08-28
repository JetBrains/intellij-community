package com.intellij.openapi.vcs.changes.pending;

import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerGate;
import com.intellij.openapi.vcs.changes.LocalChangeList;

public class MockChangeListManagerGate implements ChangeListManagerGate {
  private final ChangeListManager myManager;

  public MockChangeListManagerGate(final ChangeListManager manager) {
    myManager = manager;
  }

  public LocalChangeList findChangeList(final String name) {
    return myManager.findChangeList(name);
  }

  public LocalChangeList addChangeList(final String name, final String comment) {
    return myManager.addChangeList(name, comment);
  }

  public LocalChangeList findOrCreateList(final String name, final String comment) {
    LocalChangeList changeList = myManager.findChangeList(name);
    if (changeList == null) {
      changeList = myManager.addChangeList(name, comment);
    }
    return changeList;
  }

  public void editComment(final String name, final String comment) {
    myManager.editComment(name, comment);
  }
}

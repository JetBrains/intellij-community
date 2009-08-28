package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import java.util.List;

public class ChangesBunch {
  private final List<CommittedChangeList> myList;
  private final boolean myConsistentWithPrevious;

  public ChangesBunch(final List<CommittedChangeList> list, final boolean consistentWithPrevious) {
    myList = list;
    myConsistentWithPrevious = consistentWithPrevious;
  }

  public List<CommittedChangeList> getList() {
    return myList;
  }

  public boolean isConsistentWithPrevious() {
    return myConsistentWithPrevious;
  }
}

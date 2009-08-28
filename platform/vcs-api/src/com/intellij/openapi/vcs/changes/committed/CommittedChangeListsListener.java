package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

public interface CommittedChangeListsListener {
  void onBeforeStartReport();

  /**
   * @return true - continue reporting
   */
  boolean report(final CommittedChangeList list);
  void onAfterEndReport();
}

package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.vcs.changes.CommitExecutor;

/**
 * @author irengrig
 *         Date: 5/24/11
 *         Time: 7:23 PM
 */
public abstract class BeforeCheckinDialogHandler {
  /**
   * @return false to cancel commit
   * @param executors
   * @param showVcsCommit
   */
  public abstract boolean beforeCommitDialogShownCallback(Iterable<CommitExecutor> executors, boolean showVcsCommit);
}

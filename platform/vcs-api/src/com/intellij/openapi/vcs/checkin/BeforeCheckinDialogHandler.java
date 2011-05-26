package com.intellij.openapi.vcs.checkin;

/**
 * @author irengrig
 *         Date: 5/24/11
 *         Time: 7:23 PM
 */
public interface BeforeCheckinDialogHandler {
  /**
   * @return false to cancel commit
   */
  boolean beforeCommitDialogShownCallback();
}

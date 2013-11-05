package com.intellij.vcs.log;

import com.intellij.openapi.components.StoragePathMacros;

/**
 * <p>Vcs Log user settings, which may have a UI representation, or be implicitly selected based on user actions.</p>
 * <p>Most of the settings are workspace-specific, i. e. they are stored in {@link StoragePathMacros#WORKSPACE_FILE workspace.xml}.</p>
 *
 * @author Kirill Likhodedov
 */
public interface VcsLogSettings {

  /**
   * <p>Returns the number of recent commits which are loaded initially by default, and are always stored in memory.</p>
   *
   * <p>The more this number is, the more memory is occupied, but the faster filtering works, and more commits can be viewed back in history
   * without need to load additional details from the VCS.</p>
   */
  int getRecentCommitsCount();

  /**
   * Checks if the branches panel should be displayed or hidden.
   */
  boolean isShowBranchesPanel();

  void setShowBranchesPanel(boolean show);

}

package com.intellij.vcs.log;

import com.intellij.openapi.components.StoragePathMacros;

/**
 * <p>Vcs Log user settings, both explicit (when user chooses some behavior)
 *    and implicit (when we remember some preference based on user actions.</p>
 *
 * <p>Most of the settings are workspace-specific, i. e. they are stored in
 *    {@link StoragePathMacros#WORKSPACE_FILE .idea/workspace.xml}.</p>
 *
 * @author Kirill Likhodedov
 */
public interface VcsLogSettings {

  /**
   * Returns true if the details pane (which shows commit meta-data, such as the full commit message, commit date, all references, etc.)
   * should be visible when the log is loaded; returns false if it should be hidden by default.
   * @see #setShowDetails(boolean)
   */
  boolean isShowDetails();

  /**
   * Sets if the details pane (which shows commit meta-data) should be shown or hidden by default.
   */
  void setShowDetails(boolean showDetails);
}

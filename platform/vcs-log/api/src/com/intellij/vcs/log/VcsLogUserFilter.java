package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Filters log by user.
 */
public interface VcsLogUserFilter extends VcsLogFilter {

  /**
   * Returns the user name selected in the filter for the given root.
   * If it is a name-as-text filter, of course, values don't differ per root. The difference appears if the special "me" filter is used.
   */
  @NotNull
  String getUserName(@NotNull VirtualFile root);

}

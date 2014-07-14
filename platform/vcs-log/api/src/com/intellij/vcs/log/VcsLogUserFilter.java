package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Filters commmits by one or several users.
 */
public interface VcsLogUserFilter extends VcsLogDetailsFilter {

  /**
   * Returns user names selected in the filter, concerning the passed VCS root.
   * @param root has no effect if user chooses some user name;
   *             it is needed if user selects the predefined value "me" which means the current user.
   *             Since current user name can be defined differently for different roots, we pass the root for which this value is
   *             requested.
   */
  @NotNull
  Collection<String> getUserNames(@NotNull VirtualFile root);

}

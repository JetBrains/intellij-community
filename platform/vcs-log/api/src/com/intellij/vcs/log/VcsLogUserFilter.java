package com.intellij.vcs.log;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.vcs.log.VcsLogFilterCollection.USER_FILTER;

/**
 * Filters commits by one or several users.
 */
public interface VcsLogUserFilter extends VcsLogDetailsFilter {

  /**
   * Returns users selected in the filter, concerning the passed VCS root.
   *
   * @param root has no effect if user chooses some user name;
   *             it is needed if user selects the predefined value "me" which means the current user.
   *             Since current user name can be defined differently for different roots, we pass the root for which this value is
   *             requested.
   */
  @NotNull
  Collection<VcsUser> getUsers(@NotNull VirtualFile root);

  /**
   * Filter values in text format, such that the filter could be later restored from it.
   */
  @NotNull
  Collection<@NlsSafe String> getValuesAsText();

  @NotNull
  @Override
  default String getDisplayText() {
    return StringUtil.join(getValuesAsText(), ", ");
  }

  @NotNull
  @Override
  default VcsLogFilterCollection.FilterKey<VcsLogUserFilter> getKey() {
    return USER_FILTER;
  }
}

package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Filters log by user.
 */
public abstract class VcsLogUserFilter implements VcsLogDetailsFilter {

  /**
   * Filters by the given name or part of it.
   */
  public static class ByName extends VcsLogUserFilter {

    @NotNull private final String myUser;

    public ByName(@NotNull String user) {
      myUser = user;
    }

    @Override
    public boolean matches(@NotNull VcsFullCommitDetails detail) {
      return detail.getAuthor().getName().toLowerCase().contains(myUser.toLowerCase()) ||
             detail.getAuthor().getEmail().toLowerCase().contains(myUser.toLowerCase());
    }

    @NotNull
    @Override
    public String getUserName(@NotNull VirtualFile root) {
      return myUser;
    }
  }

  /**
   * Looks for commits matching the current user,
   * i.e. looks for the value stored in the VCS config and compares the configured name with the one returned in commit details.
   */
  public static class Me extends VcsLogUserFilter {

    @NotNull private final Map<VirtualFile, VcsUser> myMeData;

    public Me(@NotNull Map<VirtualFile, VcsUser> meData) {
      myMeData = meData;
    }

    @Override
    public boolean matches(@NotNull VcsFullCommitDetails details) {
      VcsUser meInThisRoot = myMeData.get(details.getRoot());
      return meInThisRoot != null && meInThisRoot.equals(details.getAuthor());
    }

    @NotNull
    @Override
    public String getUserName(@NotNull VirtualFile root) {
      return myMeData.get(root).getName();
    }
  }

  /**
   * Returns the user name selected in the filter for the given root.
   * If it is a name-as-text filter, of course, values don't differ per root. The difference appears if the special "me" filter is used.
   */
  @NotNull
  public abstract String getUserName(@NotNull VirtualFile root);

}

package com.intellij.vcs.log.data;

import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;

/**
 * TODO use some special User class instead of String
 */
public class VcsLogUserFilter implements VcsLogDetailsFilter {

  @NotNull private final String myUser;

  public VcsLogUserFilter(@NotNull String user) {
    myUser = user;
  }

  @Override
  public boolean matches(@NotNull VcsFullCommitDetails detail) {
    return detail.getAuthorName().toLowerCase().contains(myUser) || detail.getAuthorEmail().toLowerCase().contains(myUser);
  }

  @NotNull
  public String getUserName() {
    return myUser;
  }

}

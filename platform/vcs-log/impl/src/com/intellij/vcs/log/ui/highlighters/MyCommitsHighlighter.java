// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.highlighters;

import com.intellij.ui.ExperimentalUI;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.table.column.Author;
import com.intellij.vcs.log.ui.table.column.VcsLogColumnManager;
import com.intellij.vcs.log.util.VcsUserUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class MyCommitsHighlighter implements VcsLogHighlighter {
  private final @NotNull VcsLogData myLogData;
  private boolean myShouldHighlightUser = false;

  public MyCommitsHighlighter(@NotNull VcsLogData logData) {
    myLogData = logData;
  }

  @Override
  public @NotNull VcsCommitStyle getStyle(int commitId, @NotNull VcsShortCommitDetails details, int column, boolean isSelected) {
    if (details instanceof LoadingDetails) return VcsCommitStyle.DEFAULT;
    if (myShouldHighlightUser) {
      VcsUser currentUser = myLogData.getCurrentUser().get(details.getRoot());
      if (currentUser != null && VcsUserUtil.isSamePerson(currentUser, details.getAuthor())) {
        if (ExperimentalUI.isNewUI() && !isAuthorColumn(column)) {
          return VcsCommitStyle.DEFAULT;
        }
        return VcsCommitStyleFactory.bold();
      }
    }
    return VcsCommitStyle.DEFAULT;
  }

  private static boolean isAuthorColumn(int column) {
    return VcsLogColumnManager.getInstance().getModelIndex(Author.INSTANCE) == column;
  }

  @Override
  public void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
    myShouldHighlightUser = !isSingleUser() && !isFilteredByCurrentUser(dataPack.getFilters());
  }

  // returns true if only one user commits to this repository
  private boolean isSingleUser() {
    Set<VcsUser> users = new ObjectOpenCustomHashSet<>(myLogData.getCurrentUser().values(), new VcsUserUtil.VcsUserHashingStrategy());
    return myLogData.getUserRegistry().all(user -> users.contains(user));
  }

  // returns true if filtered by "me"
  private static boolean isFilteredByCurrentUser(@NotNull VcsLogFilterCollection filters) {
    VcsLogUserFilter userFilter = filters.get(VcsLogFilterCollection.USER_FILTER);
    if (userFilter == null) return false;
    if (Collections.singleton(VcsLogFilterObject.ME).containsAll(userFilter.getValuesAsText())) return true;
    return false;
  }

  public static class Factory implements VcsLogHighlighterFactory {
    public static final @NotNull @NonNls String ID = "MY_COMMITS";

    @Override
    public @NotNull VcsLogHighlighter createHighlighter(@NotNull VcsLogData logData, @NotNull VcsLogUi logUi) {
      return new MyCommitsHighlighter(logData);
    }

    @Override
    public @NotNull String getId() {
      return ID;
    }

    @Override
    public @NotNull String getTitle() {
      return VcsLogBundle.message("vcs.log.action.highlight.my.commits");
    }

    @Override
    public boolean showMenuItem() {
      return true;
    }
  }
}

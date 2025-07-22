// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.highlighters;

import com.intellij.ui.ExperimentalUI;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogUserResolver;
import com.intellij.vcs.log.ui.VcsLogUiEx;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.ui.table.column.Author;
import com.intellij.vcs.log.ui.table.column.VcsLogColumnManager;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

@ApiStatus.Internal
public class VcsLogCommitsHighlighter implements VcsLogHighlighter {
  private final @NotNull VcsLogData myLogData;
  private final @NotNull VcsLogUi myUi;
  private final @NotNull VcsLogUserResolver myResolver;
  private boolean myShouldHighlightUser = false;

  VcsLogCommitsHighlighter(@NotNull VcsLogData logData, @NotNull VcsLogUi logUi, @NotNull VcsLogUserResolver userResolver) {
    myLogData = logData;
    myUi = logUi;
    myResolver = userResolver;
  }

  @Override
  public @NotNull VcsCommitStyle getStyle(int commitId, @NotNull VcsShortCommitDetails details, int column, boolean isSelected) {
    if (details instanceof LoadingDetails) return VcsCommitStyle.DEFAULT;
    if (myShouldHighlightUser) {
      Set<VcsUser> currentUsers = myResolver.resolveCurrentUser(details.getRoot());
      if (currentUsers.contains(details.getAuthor())) {
        if (ExperimentalUI.isNewUI() && isAuthorColumnVisible() && !isAuthorColumn(column)) {
          return VcsCommitStyle.DEFAULT;
        }
        return VcsCommitStyleFactory.bold();
      }
    }
    return VcsCommitStyle.DEFAULT;
  }

  private boolean isAuthorColumnVisible() {
    if (myUi instanceof VcsLogUiEx ui) {
      if (ui.getTable() instanceof VcsLogGraphTable table) {
        return table.isColumnVisible(Author.INSTANCE);
      }
    }
    return true;
  }

  private static boolean isAuthorColumn(int column) {
    return VcsLogColumnManager.getInstance().getModelIndex(Author.INSTANCE) == column;
  }

  @Override
  public void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
    myShouldHighlightUser = !myLogData.isSingleUser() && !isFilteredByCurrentUser(dataPack.getFilters());
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
      return new VcsLogCommitsHighlighter(logData, logUi, logData.getUserNameResolver());
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

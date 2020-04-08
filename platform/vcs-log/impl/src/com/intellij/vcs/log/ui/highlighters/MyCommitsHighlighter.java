/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui.highlighters;

import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.util.VcsUserUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class MyCommitsHighlighter implements VcsLogHighlighter {
  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogUi myLogUi;
  private boolean myShouldHighlightUser = false;

  public MyCommitsHighlighter(@NotNull VcsLogData logData, @NotNull VcsLogUi logUi) {
    myLogData = logData;
    myLogUi = logUi;
  }

  @NotNull
  @Override
  public VcsCommitStyle getStyle(int commitId, @NotNull VcsShortCommitDetails details, boolean isSelected) {
    if (!myLogUi.isHighlighterEnabled(Factory.ID)) return VcsCommitStyle.DEFAULT;
    if (myShouldHighlightUser) {
      VcsUser currentUser = myLogData.getCurrentUser().get(details.getRoot());
      if (currentUser != null && VcsUserUtil.isSamePerson(currentUser, details.getAuthor())) {
        return VcsCommitStyleFactory.bold();
      }
    }
    return VcsCommitStyle.DEFAULT;
  }

  @Override
  public void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
    myShouldHighlightUser = !isSingleUser() && !isFilteredByCurrentUser(dataPack.getFilters());
  }

  // returns true if only one user commits to this repository
  private boolean isSingleUser() {
    THashSet<VcsUser> users = new THashSet<>(myLogData.getCurrentUser().values(), new VcsUserUtil.VcsUserHashingStrategy());
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
    @NotNull public static final String ID = "MY_COMMITS"; // NON-NLS

    @NotNull
    @Override
    public VcsLogHighlighter createHighlighter(@NotNull VcsLogData logData, @NotNull VcsLogUi logUi) {
      return new MyCommitsHighlighter(logData, logUi);
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @NotNull
    @Override
    public String getTitle() {
      return VcsLogBundle.message("vcs.log.action.highlight.my.commits");
    }

    @Override
    public boolean showMenuItem() {
      return true;
    }
  }
}

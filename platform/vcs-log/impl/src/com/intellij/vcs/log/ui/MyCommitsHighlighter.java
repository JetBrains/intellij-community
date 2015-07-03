/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsUserImpl;
import com.intellij.vcs.log.ui.filter.VcsLogUserFilterImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class MyCommitsHighlighter implements VcsLogHighlighter {
  @NotNull private final VcsLogUiProperties myUiProperties;
  @NotNull private final VcsLogDataHolder myDataHolder;
  @NotNull private final VcsLogFilterUi myFilterUi;

  public MyCommitsHighlighter(@NotNull VcsLogDataHolder logDataHolder,
                              @NotNull VcsLogUiProperties uiProperties,
                              @NotNull VcsLogFilterUi filterUi) {
    myDataHolder = logDataHolder;
    myUiProperties = uiProperties;
    myFilterUi = filterUi;
    // migration to map storage
    if (!myUiProperties.isHighlightMyCommits()) {
      // by default, my commits highlighter was enabled
      // if it was disabled we need to migrate that
      myUiProperties.enableHighlighter(Factory.ID, false);
      myUiProperties.setHighlightMyCommits(true);
    }
  }

  @NotNull
  @Override
  public VcsCommitStyle getStyle(int commitIndex, boolean isSelected) {
    if (!myUiProperties.isHighlighterEnabled(Factory.ID)) return VcsCommitStyle.DEFAULT;
    Map<VirtualFile, VcsUser> currentUsers = myDataHolder.getCurrentUser();
    if (!areTheOnlyUsers(currentUsers) && !isFilteredByCurrentUser()) {
      VcsShortCommitDetails details = myDataHolder.getMiniDetailsGetter().getCommitDataIfAvailable(commitIndex);
      if (details != null && !(details instanceof LoadingDetails)) {
        VcsUser currentUser = currentUsers.get(details.getRoot());
        if (currentUser != null && VcsUserImpl.isSamePerson(currentUser, details.getAuthor())) {
          return VcsCommitStyleFactory.bold();
        }
      }
    }
    return VcsCommitStyle.DEFAULT;
  }

  private boolean areTheOnlyUsers(@NotNull Map<VirtualFile, VcsUser> currentUsers) {
    Set<VcsUser> allUsers = myDataHolder.getAllUsers();
    return allUsers.size() == currentUsers.values().size() && currentUsers.values().containsAll(allUsers);
  }

  private boolean isFilteredByCurrentUser() {
    VcsLogUserFilter userFilter = myFilterUi.getFilters().getUserFilter();
    if (userFilter == null) return false;
    Collection<String> filterByName = ((VcsLogUserFilterImpl)userFilter).getUserNamesForPresentation();
    if (Collections.singleton(VcsLogUserFilterImpl.ME).containsAll(filterByName)) return true;
    return false;
  }

  public static class Factory implements VcsLogHighlighterFactory {
    @NotNull
    private static final String ID = "MY_COMMITS";

    @NotNull
    @Override
    public VcsLogHighlighter createHighlighter(@NotNull VcsLogDataHolder logDataHolder,
                                               @NotNull VcsLogUiProperties uiProperties,
                                               @NotNull VcsLogFilterUi filterUi) {
      return new MyCommitsHighlighter(logDataHolder, uiProperties, filterUi);
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @NotNull
    @Override
    public String getTitle() {
      return "My Commits";
    }
  }
}

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

import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.impl.VcsUserImpl;
import com.intellij.vcs.log.ui.filter.VcsLogUserFilterImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class MyCommitsHighlighter implements VcsLogHighlighter {
  @NotNull private final VcsLogDataHolder myDataHolder;
  @NotNull private final VcsLogUi myLogUi;
  private boolean myShouldHighlightUser = false;

  public MyCommitsHighlighter(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUi logUi) {
    myDataHolder = logDataHolder;
    myLogUi = logUi;
  }

  @NotNull
  @Override
  public VcsCommitStyle getStyle(@NotNull VcsShortCommitDetails details, boolean isSelected) {
    if (!myLogUi.isHighlighterEnabled(Factory.ID)) return VcsCommitStyle.DEFAULT;
    if (myShouldHighlightUser) {
      VcsUser currentUser = myDataHolder.getCurrentUser().get(details.getRoot());
      if (currentUser != null && VcsUserImpl.isSamePerson(currentUser, details.getAuthor())) {
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
    NotNullFunction<VcsUser, String> nameToString = new NotNullFunction<VcsUser, String>() {
      @NotNull
      @Override
      public String fun(VcsUser user) {
        return VcsUserImpl.getNameInStandardForm(user.getName());
      }
    };
    Set<String> allUserNames = ContainerUtil.newHashSet(ContainerUtil.map(myDataHolder.getAllUsers(), nameToString));
    Set<String> currentUserNames = ContainerUtil.newHashSet(ContainerUtil.map(myDataHolder.getCurrentUser().values(), nameToString));
    return allUserNames.size() == currentUserNames.size() && currentUserNames.containsAll(allUserNames);
  }

  // returns true if filtered by "me"
  private static boolean isFilteredByCurrentUser(@NotNull VcsLogFilterCollection filters) {
    VcsLogUserFilter userFilter = filters.getUserFilter();
    if (userFilter == null) return false;
    Collection<String> filterByName = ((VcsLogUserFilterImpl)userFilter).getUserNamesForPresentation();
    if (Collections.singleton(VcsLogUserFilterImpl.ME).containsAll(filterByName)) return true;
    return false;
  }

  public static class Factory implements VcsLogHighlighterFactory {
    @NotNull private static final String ID = "MY_COMMITS";

    @NotNull
    @Override
    public VcsLogHighlighter createHighlighter(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUi logUi) {
      return new MyCommitsHighlighter(logDataHolder, logUi);
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

    @Override
    public boolean showMenuItem() {
      return true;
    }
  }
}

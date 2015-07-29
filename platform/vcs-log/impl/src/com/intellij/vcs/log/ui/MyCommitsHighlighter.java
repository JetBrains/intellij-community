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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogContentProvider;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsUserImpl;
import com.intellij.vcs.log.ui.filter.VcsLogUserFilterImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class MyCommitsHighlighter implements VcsLogHighlighter {
  @NotNull private final VcsLogUiProperties myUiProperties;
  @NotNull private final VcsLogDataHolder myDataHolder;
  @NotNull private final VcsLogFilterUi myFilterUi;
  private boolean myAreTheOnlyUsers = false;

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

    // this is a tmp solution for performance problems of calculating areTheOnlyUsers every repaint (we simply do not want to do that)
    // todo remove this when history2 branch is merged into master (history2 will allow a proper way to fix the problem)
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        VcsLogManager logManager = VcsLogContentProvider.findLogManager(myDataHolder.getProject());
        if (logManager != null) {
          VcsLogUiImpl logUi = logManager.getLogUi();
          if (logUi != null) {
            logUi.addLogListener(new VcsLogListener() {
              @Override
              public void onChange(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
                myAreTheOnlyUsers = areTheOnlyUsers();
              }
            });
          }
        }
      }
    });
  }

  @NotNull
  @Override
  public VcsCommitStyle getStyle(int commitIndex, boolean isSelected) {
    if (!myUiProperties.isHighlighterEnabled(Factory.ID)) return VcsCommitStyle.DEFAULT;
    if (!myAreTheOnlyUsers && !isFilteredByCurrentUser()) {
      VcsShortCommitDetails details = myDataHolder.getMiniDetailsGetter().getCommitDataIfAvailable(commitIndex);
      if (details != null && !(details instanceof LoadingDetails)) {
        VcsUser currentUser = myDataHolder.getCurrentUser().get(details.getRoot());
        if (currentUser != null && VcsUserImpl.isSamePerson(currentUser, details.getAuthor())) {
          return VcsCommitStyleFactory.bold();
        }
      }
    }
    return VcsCommitStyle.DEFAULT;
  }

  private boolean areTheOnlyUsers() {
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

  private boolean isFilteredByCurrentUser() {
    VcsLogUserFilter userFilter = myFilterUi.getFilters().getUserFilter();
    if (userFilter == null) return false;
    Collection<String> filterByName = ((VcsLogUserFilterImpl)userFilter).getUserNamesForPresentation();
    if (Collections.singleton(VcsLogUserFilterImpl.ME).containsAll(filterByName)) return true;
    return false;
  }

  public static class Factory implements VcsLogHighlighterFactory {
    @NotNull private static final String ID = "MY_COMMITS";

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

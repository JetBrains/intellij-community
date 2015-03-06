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
import com.intellij.vcs.log.VcsCommitStyleFactory;
import com.intellij.vcs.log.VcsLogHighlighter;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsUserImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class MyCommitsHighlighter implements VcsLogHighlighter {
  @NotNull private final VcsLogUiProperties myUiProperties;
  @NotNull private final VcsLogDataHolder myDataHolder;

  public MyCommitsHighlighter(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUiProperties uiProperties) {
    myDataHolder = logDataHolder;
    myUiProperties = uiProperties;
  }

  @NotNull
  @Override
  public VcsCommitStyle getStyle(int commitIndex, boolean isSelected) {
    if (isSelected || !myUiProperties.isHighlightMyCommits()) return VcsCommitStyle.DEFAULT;
    Map<VirtualFile, VcsUser> users = myDataHolder.getCurrentUser();
    VcsShortCommitDetails details = myDataHolder.getMiniDetailsGetter().getCommitDataIfAvailable(commitIndex);
    if (details != null && !(details instanceof LoadingDetails)) {
      VcsUser currentUser = users.get(details.getRoot());
      if (currentUser != null && VcsUserImpl.isSamePerson(currentUser, details.getAuthor())) {
        return VcsCommitStyleFactory.bold();
      }
    }
    return VcsCommitStyle.DEFAULT;
  }

  public static class Creator implements VcsLogHighlighterCreator {
    @NotNull
    @Override
    public VcsLogHighlighter createHighlighter(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUiProperties uiProperties) {
      return new MyCommitsHighlighter(logDataHolder, uiProperties);
    }
  }
}

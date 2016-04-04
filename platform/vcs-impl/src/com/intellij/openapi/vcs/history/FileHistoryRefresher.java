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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Refreshes file history.
 * @author irengrig
 * @author Kirill Likhodedov
 */
public class FileHistoryRefresher implements FileHistoryRefresherI {
  private final FileHistorySessionPartner mySessionPartner;
  private final VcsHistoryProvider myVcsHistoryProvider;
  private final FilePath myPath;
  private final AbstractVcs myVcs;
  @Nullable private final VcsRevisionNumber myStartingRevisionNumber;
  private boolean myCanUseCache;
  private boolean myIsRefresh;

  public FileHistoryRefresher(final VcsHistoryProvider vcsHistoryProvider,
                              final FilePath path,
                              final AbstractVcs vcs) {
    this(vcsHistoryProvider, path, null, vcs);
  }
  
  public FileHistoryRefresher(final VcsHistoryProviderEx vcsHistoryProvider,
                              final FilePath path,
                              @Nullable VcsRevisionNumber startingRevisionNumber, 
                              final AbstractVcs vcs) {
    this((VcsHistoryProvider)vcsHistoryProvider, path, startingRevisionNumber, vcs);
  }
  
  private FileHistoryRefresher(final VcsHistoryProvider vcsHistoryProvider,
                               final FilePath path,
                               @Nullable VcsRevisionNumber startingRevisionNumber, 
                               final AbstractVcs vcs) {
    myVcsHistoryProvider = vcsHistoryProvider;
    myPath = path;
    myVcs = vcs;
    myStartingRevisionNumber = startingRevisionNumber;
    mySessionPartner = new FileHistorySessionPartner(vcsHistoryProvider, path, startingRevisionNumber, vcs, this);
    myCanUseCache = true;
  }

  @NotNull
  public static FileHistoryRefresherI findOrCreate(@NotNull VcsHistoryProvider vcsHistoryProvider,
                                                   @NotNull FilePath path,
                                                   @NotNull AbstractVcs vcs) {
    FileHistoryRefresherI refresher = FileHistorySessionPartner.findExistingHistoryRefresher(vcs.getProject(), path, null);
    return refresher == null ? new FileHistoryRefresher(vcsHistoryProvider, path, vcs) : refresher;
  }

  @NotNull
  public static FileHistoryRefresherI findOrCreate(@NotNull VcsHistoryProviderEx vcsHistoryProvider,
                                                   @NotNull FilePath path,
                                                   @NotNull AbstractVcs vcs,
                                                   @Nullable VcsRevisionNumber startingRevisionNumber) {
    FileHistoryRefresherI refresher = FileHistorySessionPartner.findExistingHistoryRefresher(vcs.getProject(), path, startingRevisionNumber);
    return refresher == null ? new FileHistoryRefresher(vcsHistoryProvider, path, startingRevisionNumber, vcs) : refresher;
  }

  /**
   * @param canUseLastRevision
   */
  @Override
  public void run(boolean isRefresh, boolean canUseLastRevision) {
    myIsRefresh = isRefresh;
    mySessionPartner.beforeRefresh();
    final VcsHistoryProviderBackgroundableProxy proxy = new VcsHistoryProviderBackgroundableProxy(
      myVcs, myVcsHistoryProvider, myVcs.getDiffProvider());
    if (myVcsHistoryProvider instanceof VcsHistoryProviderEx && myStartingRevisionNumber != null) {
      proxy.executeAppendableSession(myVcs.getKeyInstanceMethod(), myPath, myStartingRevisionNumber, mySessionPartner, null);
    }
    else {
      proxy.executeAppendableSession(myVcs.getKeyInstanceMethod(), myPath, mySessionPartner, null, myCanUseCache, canUseLastRevision);
    }
    myCanUseCache = false;
  }

  /**
   * Was the refresher called for the first time or via refresh.
   * @return
   */
  @Override
  public boolean isFirstTime() {
    return !myIsRefresh;
  }
}

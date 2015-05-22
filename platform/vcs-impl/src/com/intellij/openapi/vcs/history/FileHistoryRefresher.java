/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

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
  private boolean myCanUseCache;
  private boolean myIsRefresh;

  public FileHistoryRefresher(final VcsHistoryProvider vcsHistoryProvider,
                              final FilePath path,
                              final AbstractVcs vcs) {
    myVcsHistoryProvider = vcsHistoryProvider;
    myPath = path;
    myVcs = vcs;
    mySessionPartner = new FileHistorySessionPartner(vcsHistoryProvider, path, vcs, this);
    myCanUseCache = true;
  }

  @NotNull
  public static FileHistoryRefresherI findOrCreate(@NotNull VcsHistoryProvider vcsHistoryProvider,
                                                   @NotNull FilePath path,
                                                   @NotNull AbstractVcs vcs) {
    FileHistoryRefresherI refresher = FileHistorySessionPartner.findExistingHistoryRefresher(vcs.getProject(), path);
    return refresher == null ? new FileHistoryRefresher(vcsHistoryProvider, path, vcs) : refresher;
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
    proxy.executeAppendableSession(myVcs.getKeyInstanceMethod(), myPath, mySessionPartner, null, myCanUseCache, canUseLastRevision);
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

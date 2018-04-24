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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Refreshes file history.
 *
 * @author irengrig
 * @author Kirill Likhodedov
 */
public class FileHistoryRefresher implements FileHistoryRefresherI {
  private static final ExecutorService ourExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("File History Refresh");
  private final FileHistorySessionPartner mySessionPartner;
  private final VcsHistoryProvider myVcsHistoryProvider;
  private final FilePath myPath;
  private final AbstractVcs myVcs;
  @Nullable private final VcsRevisionNumber myStartingRevisionNumber;
  private boolean myFirstTime = true;

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

    int delayMillis = 20_000;
    Alarm updateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, mySessionPartner);
    updateAlarm.addRequest(new Runnable() {
      Future<?> lastTask;

      public void run() {
        if (lastTask != null) {
          lastTask.cancel(false);
        }
        if (myVcs.getProject().isDisposed()) {
          return;
        }

        updateAlarm.cancelAllRequests();
        if (updateAlarm.isDisposed()) return;
        updateAlarm.addRequest(this, delayMillis);

        if (!ApplicationManager.getApplication().isActive()) return;

        lastTask = ourExecutor.submit(() -> {
          if (!updateAlarm.isDisposed() && mySessionPartner.shouldBeRefreshed()) {
            ApplicationManager.getApplication().invokeLater(() -> refresh(true));
          }
        });
      }
    }, delayMillis);
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
    FileHistoryRefresherI refresher =
      FileHistorySessionPartner.findExistingHistoryRefresher(vcs.getProject(), path, startingRevisionNumber);
    return refresher == null ? new FileHistoryRefresher(vcsHistoryProvider, path, startingRevisionNumber, vcs) : refresher;
  }

  @Override
  public void selectContent() {
    mySessionPartner.createOrSelectContent();
  }

  @Override
  public boolean isInRefresh() {
    return VcsCachingHistory.getHistoryLock(myVcs, VcsBackgroundableActions.CREATE_HISTORY_SESSION, myPath, myStartingRevisionNumber)
                            .isLocked();
  }

  /**
   * @param canUseCache
   */
  @Override
  public void refresh(boolean canUseCache) {
    mySessionPartner.beforeRefresh();

    if (myVcsHistoryProvider instanceof VcsHistoryProviderEx && myStartingRevisionNumber != null) {
      VcsCachingHistory.collectInBackground(myVcs, myPath, myStartingRevisionNumber, mySessionPartner);
    }
    else {
      boolean collectedFromCache = false;
      if (myFirstTime) {
        collectedFromCache = VcsCachingHistory.collectFromCache(myVcs, myPath, mySessionPartner);
      }

      if (!collectedFromCache) {
        VcsCachingHistory.collectInBackground(myVcs, myPath, mySessionPartner, canUseCache);
      }
    }

    myFirstTime = false;
  }
}

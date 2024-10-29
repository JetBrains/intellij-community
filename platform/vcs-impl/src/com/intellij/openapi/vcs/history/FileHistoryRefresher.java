// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Refreshes file history.
 *
 * @author Kirill Likhodedov
 */
@ApiStatus.Internal
public class FileHistoryRefresher implements FileHistoryRefresherI {
  @NotNull private static final ExecutorService ourExecutor =
    SequentialTaskExecutor.createSequentialApplicationPoolExecutor("File History Refresh");
  @NotNull private final FileHistorySessionPartner mySessionPartner;
  @NotNull private final VcsHistoryProvider myVcsHistoryProvider;
  @NotNull private final FilePath myPath;
  @NotNull private final AbstractVcs myVcs;
  @Nullable private final VcsRevisionNumber myStartingRevisionNumber;
  private boolean myFirstTime = true;

  public FileHistoryRefresher(@NotNull VcsHistoryProvider vcsHistoryProvider,
                              @NotNull FilePath path,
                              @NotNull AbstractVcs vcs) {
    this(vcsHistoryProvider, path, null, vcs);
  }

  public FileHistoryRefresher(@NotNull VcsHistoryProviderEx vcsHistoryProvider,
                              @NotNull FilePath path,
                              @Nullable VcsRevisionNumber startingRevisionNumber,
                              @NotNull AbstractVcs vcs) {
    this((VcsHistoryProvider)vcsHistoryProvider, path, startingRevisionNumber, vcs);
  }

  private FileHistoryRefresher(@NotNull VcsHistoryProvider vcsHistoryProvider,
                               @NotNull FilePath path,
                               @Nullable VcsRevisionNumber startingRevisionNumber,
                               @NotNull AbstractVcs vcs) {
    myVcsHistoryProvider = vcsHistoryProvider;
    myPath = path;
    myVcs = vcs;
    myStartingRevisionNumber = startingRevisionNumber;
    mySessionPartner = new FileHistorySessionPartner(vcsHistoryProvider, path, startingRevisionNumber, vcs, this);

    RefreshRequest request = new RefreshRequest(20_000, mySessionPartner);
    request.schedule();
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

  private class RefreshRequest implements Runnable {
    @NotNull private final Alarm myUpdateAlarm;
    private final int myDelayMillis;
    @Nullable Future<?> myLastTask;

    RefreshRequest(int delayMillis, @NotNull Disposable parent) {
      myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, parent);
      myDelayMillis = delayMillis;
    }

    @Override
    public void run() {
      if (myLastTask != null) {
        myLastTask.cancel(false);
      }
      if (myVcs.getProject().isDisposed()) {
        return;
      }

      myUpdateAlarm.cancelAllRequests();
      if (myUpdateAlarm.isDisposed()) return;
      schedule();

      if (!ApplicationManager.getApplication().isActive()) return;

      myLastTask = ourExecutor.submit(() -> {
        if (!myUpdateAlarm.isDisposed() && mySessionPartner.shouldBeRefreshed()) {
          ApplicationManager.getApplication().invokeLater(() -> refresh(true));
        }
      });
    }

    public void schedule() {
      myUpdateAlarm.addRequest(this, myDelayMillis);
    }
  }
}

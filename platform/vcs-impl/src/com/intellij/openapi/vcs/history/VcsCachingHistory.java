// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.LimitHistoryCheck.VcsFileHistoryLimitReachedException;
import com.intellij.openapi.vcs.impl.BackgroundableActionLock;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.util.Consumer;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.List;

import static com.intellij.openapi.vcs.impl.BackgroundableActionLock.getLock;
import static com.intellij.util.ObjectUtils.notNull;

public class VcsCachingHistory {
  @NotNull private final Project myProject;
  @NotNull private final VcsHistoryCache myVcsHistoryCache;
  @NotNull private final VcsHistoryProvider myHistoryProvider;
  @NotNull private final VcsType myType;
  private final DiffProvider myDiffProvider;

  private VcsCachingHistory(@NotNull AbstractVcs vcs,
                            @NotNull VcsHistoryProvider historyProvider,
                            DiffProvider diffProvider) {
    myProject = vcs.getProject();
    myVcsHistoryCache = ProjectLevelVcsManager.getInstance(myProject).getVcsHistoryCache();
    myHistoryProvider = historyProvider;
    myType = vcs.getType();
    myDiffProvider = diffProvider;
  }

  private void reportHistoryInBackground(@NotNull FilePath filePath, @Nullable VcsRevisionNumber startRevisionNumber,
                                         @NotNull VcsKey vcsKey, @NotNull BackgroundableActionLock lock,
                                         @NotNull VcsHistorySessionConsumer partner, boolean canUseCache) {
    if (lock.isLocked()) return;
    lock.lock();

    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, VcsBundle.message("loading.file.history.progress"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText(VcsUtil.getPathForProgressPresentation(filePath.getIOFile()));
        indicator.setIndeterminate(true);
        reportHistory(filePath, startRevisionNumber, vcsKey, partner, canUseCache);
      }

      @Override
      public void onFinished() {
        lock.unlock();
      }
    });
  }

  private void reportHistory(@NotNull FilePath filePath, @Nullable VcsRevisionNumber startRevisionNumber,
                             @NotNull VcsKey vcsKey, @NotNull VcsHistorySessionConsumer partner, boolean canUseCache) {
    try {
      if (startRevisionNumber == null) {
        partner = wrapPartnerToCachingPartner(vcsKey, filePath, partner);
      }

      VcsAbstractHistorySession cachedSession = null;
      VcsCacheableHistorySessionFactory<Serializable, VcsAbstractHistorySession> cacheableFactory = getCacheableFactory();
      if (canUseCache && cacheableFactory != null) {
        cachedSession = getSessionFromCacheWithLastRevisionCheck(filePath, vcsKey, cacheableFactory);
      }


      if (cachedSession != null) {
        partner.reportCreatedEmptySession(cachedSession);
      }
      else if (myHistoryProvider instanceof VcsHistoryProviderEx) {
        ((VcsHistoryProviderEx)myHistoryProvider).reportAppendableHistory(filePath, startRevisionNumber, partner);
      }
      else {
        myHistoryProvider.reportAppendableHistory(filePath, partner);
      }
      partner.finished();
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (VcsFileHistoryLimitReachedException ignored) {
      partner.finished();
    }
    catch (VcsException e) {
      partner.reportException(e);
    }
    catch (Throwable t) {
      partner.reportException(new VcsException(t));
    }
  }

  @Nullable
  private VcsCacheableHistorySessionFactory<Serializable, VcsAbstractHistorySession> getCacheableFactory() {
    if (!(myHistoryProvider instanceof VcsCacheableHistorySessionFactory)) return null;
    //noinspection unchecked
    return (VcsCacheableHistorySessionFactory<Serializable, VcsAbstractHistorySession>)myHistoryProvider;
  }

  @NotNull
  private VcsHistoryCache getHistoryCache() {
    return myVcsHistoryCache;
  }

  @NotNull
  private VcsHistorySessionConsumer wrapPartnerToCachingPartner(@NotNull VcsKey vcsKey,
                                                                @NotNull FilePath filePath,
                                                                @NotNull VcsHistorySessionConsumer partner) {
    // this is what needs to be done to put computed file history in cache
    // we can not retrieve a session from a partner in any other way
    VcsCacheableHistorySessionFactory<Serializable, VcsAbstractHistorySession> cacheableFactory = getCacheableFactory();
    if (cacheableFactory != null) {
      return new HistoryPartnerProxy(partner, session -> {
        if (session == null) return;
        FilePath correctedPath = cacheableFactory.getUsedFilePath(session);
        myVcsHistoryCache.put(filePath, correctedPath, vcsKey, (VcsAbstractHistorySession)session.copy(), cacheableFactory, true);
      });
    }
    return partner;
  }

  @Nullable
  private VcsAbstractHistorySession getSessionFromCacheWithLastRevisionCheck(@NotNull FilePath filePath,
                                                                             @NotNull VcsKey vcsKey,
                                                                             @NotNull VcsCacheableHistorySessionFactory<Serializable, VcsAbstractHistorySession> cacheableFactory) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText2("Checking last revision");
    }
    VcsAbstractHistorySession cached = myVcsHistoryCache.getFull(filePath, vcsKey, cacheableFactory);
    if (cached == null || cached.getRevisionList().isEmpty()) return null;

    FilePath correctedFilePath = cacheableFactory.getUsedFilePath(cached);
    FilePath path = correctedFilePath != null ? correctedFilePath : filePath;

    VcsRevisionNumber currentRevision = null;
    if (VcsType.distributed.equals(myType)) {
      currentRevision = cached.calcCurrentRevisionNumber();
    }
    else {
      ItemLatestState lastRevision = myDiffProvider.getLastRevision(path);
      if (lastRevision != null && !lastRevision.isDefaultHead() && lastRevision.isItemExists()) {
        currentRevision = lastRevision.getNumber();
      }
    }

    VcsRevisionNumber firstCachedRevision = cached.getRevisionList().get(0).getRevisionNumber();
    if (currentRevision != null && firstCachedRevision.equals(currentRevision)) {
      return cached;
    }
    return null;
  }

  @CalledInBackground
  public static List<VcsFileRevision> collect(@NotNull AbstractVcs vcs,
                                              @NotNull FilePath filePath,
                                              @Nullable VcsRevisionNumber revision) throws VcsException {
    VcsCachingHistory history = new VcsCachingHistory(vcs, notNull(vcs.getVcsHistoryProvider()), vcs.getDiffProvider());
    CollectingHistorySessionConsumer partner = new CollectingHistorySessionConsumer();
    history.reportHistory(filePath, revision, vcs.getKeyInstanceMethod(), partner, true);
    partner.check();
    return partner.getSession().getRevisionList();
  }

  @CalledInAwt
  public static void collectInBackground(@NotNull AbstractVcs vcs,
                                         @NotNull FilePath filePath,
                                         @NotNull VcsBackgroundableActions actionKey,
                                         @NotNull Consumer<? super VcsHistorySession> consumer) {
    VcsCachingHistory history = new VcsCachingHistory(vcs, notNull(vcs.getVcsHistoryProvider()), vcs.getDiffProvider());
    CollectingHistoryPartner partner = new CollectingHistoryPartner(vcs.getProject(), filePath, consumer);
    BackgroundableActionLock lock = getHistoryLock(vcs, actionKey, filePath, null);
    history.reportHistoryInBackground(filePath, null, vcs.getKeyInstanceMethod(), lock, partner, true);
  }

  @CalledInAwt
  public static void collectInBackground(@NotNull AbstractVcs vcs,
                                         @NotNull FilePath filePath,
                                         @NotNull VcsHistorySessionConsumer partner,
                                         boolean canUseCache) {
    VcsCachingHistory history = new VcsCachingHistory(vcs, notNull(vcs.getVcsHistoryProvider()), vcs.getDiffProvider());
    BackgroundableActionLock lock = getHistorySessionLock(vcs, filePath, null);
    history.reportHistoryInBackground(filePath, null, vcs.getKeyInstanceMethod(), lock, partner, canUseCache);
  }

  @CalledInAwt
  public static void collectInBackground(@NotNull AbstractVcs vcs,
                                         @NotNull FilePath filePath, @NotNull VcsRevisionNumber startRevisionNumber,
                                         @NotNull VcsHistorySessionConsumer partner) {
    if (!(vcs.getVcsHistoryProvider() instanceof VcsHistoryProviderEx)) throw new UnsupportedOperationException();

    BackgroundableActionLock lock = getHistorySessionLock(vcs, filePath, startRevisionNumber);

    VcsCachingHistory history = new VcsCachingHistory(vcs, notNull(vcs.getVcsHistoryProvider()), vcs.getDiffProvider());
    history.reportHistoryInBackground(filePath, startRevisionNumber, vcs.getKeyInstanceMethod(), lock, partner, false);
  }

  @CalledInAwt
  public static boolean collectFromCache(@NotNull AbstractVcs vcs,
                                         @NotNull FilePath filePath,
                                         @NotNull VcsHistorySessionConsumer partner) {
    VcsCachingHistory history = new VcsCachingHistory(vcs, notNull(vcs.getVcsHistoryProvider()), vcs.getDiffProvider());

    VcsCacheableHistorySessionFactory<Serializable, VcsAbstractHistorySession> cacheableFactory = history.getCacheableFactory();
    if (cacheableFactory != null) {
      VcsAbstractHistorySession session = history.getHistoryCache().getFull(filePath, vcs.getKeyInstanceMethod(), cacheableFactory);
      if (session != null) {
        partner.reportCreatedEmptySession(session);
        partner.finished();
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static BackgroundableActionLock getHistorySessionLock(@NotNull AbstractVcs vcs,
                                                               @NotNull FilePath filePath,
                                                               @Nullable VcsRevisionNumber startRevisionNumber) {
    return getHistoryLock(vcs, VcsBackgroundableActions.CREATE_HISTORY_SESSION, filePath, startRevisionNumber);
  }

  @NotNull
  public static BackgroundableActionLock getHistoryLock(@NotNull AbstractVcs vcs,
                                                        @NotNull VcsBackgroundableActions actionKey,
                                                        @NotNull FilePath filePath,
                                                        @Nullable VcsRevisionNumber startRevisionNumber) {
    if (startRevisionNumber != null) {
      return getLock(vcs.getProject(), actionKey, filePath.getPath(), startRevisionNumber.asString());
    }
    return getLock(vcs.getProject(), actionKey, filePath.getPath());
  }

  private static class CollectingHistoryPartner implements VcsHistorySessionConsumer {
    @NotNull private final Project myProject;
    @NotNull private final FilePath myFilePath;
    @NotNull private final Consumer<? super VcsHistorySession> myContinuation;
    @NotNull private final LimitHistoryCheck myCheck;

    private VcsAbstractHistorySession mySession;

    private CollectingHistoryPartner(@NotNull Project project, @NotNull FilePath path,
                                     @NotNull Consumer<? super VcsHistorySession> continuation) {
      myProject = project;
      myFilePath = path;
      myContinuation = continuation;
      myCheck = new LimitHistoryCheck(myProject, myFilePath.getPath());
    }

    @Override
    public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
      List<VcsFileRevision> revisionList = session.getRevisionList();
      while (myCheck.isOver(revisionList.size())) revisionList.remove(revisionList.size() - 1);
      mySession = session;
    }

    @Override
    public void acceptRevision(VcsFileRevision revision) {
      myCheck.checkNumber();
      mySession.appendRevision(revision);
    }

    @Override
    public void reportException(VcsException exception) {
      AbstractVcsHelper.getInstance(myProject).showError(exception,
                                                         VcsBundle.message("message.title.could.not.load.file.history"));
    }

    @Override
    public void finished() {
      if (mySession != null) {
        ApplicationManager.getApplication().invokeLater(() -> myContinuation.consume(mySession), ModalityState.defaultModalityState());
      }
    }
  }

  private static class HistoryPartnerProxy implements VcsHistorySessionConsumer {
    @NotNull private final VcsHistorySessionConsumer myPartner;
    @NotNull private final Consumer<? super VcsAbstractHistorySession> myFinish;
    private VcsAbstractHistorySession myCopy;

    private HistoryPartnerProxy(@NotNull VcsHistorySessionConsumer partner, @NotNull Consumer<? super VcsAbstractHistorySession> finish) {
      myPartner = partner;
      myFinish = finish;
    }

    @Override
    public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
      myCopy = (VcsAbstractHistorySession)session.copy();
      myPartner.reportCreatedEmptySession(session);
    }

    @Override
    public void acceptRevision(VcsFileRevision revision) {
      myCopy.appendRevision(revision);
      myPartner.acceptRevision(revision);
    }

    @Override
    public void reportException(VcsException exception) {
      myPartner.reportException(exception);
    }

    @Override
    public void finished() {
      myPartner.finished();
      myFinish.consume(myCopy);
    }
  }
}

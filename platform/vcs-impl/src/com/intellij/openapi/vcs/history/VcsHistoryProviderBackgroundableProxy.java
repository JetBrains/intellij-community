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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.impl.BackgroundableActionLock;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vcs.impl.VcsBackgroundableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * also uses memory cache
 */
public class VcsHistoryProviderBackgroundableProxy {
  @NotNull private final Project myProject;
  @NotNull private final VcsHistoryCache myVcsHistoryCache;
  @NotNull private final VcsConfiguration myConfiguration;
  @NotNull private final VcsHistoryProvider myHistoryProvider;
  @NotNull private final VcsType myType;
  private final DiffProvider myDiffProvider;

  public VcsHistoryProviderBackgroundableProxy(@NotNull AbstractVcs vcs,
                                               @NotNull VcsHistoryProvider historyProvider,
                                               DiffProvider diffProvider) {
    myProject = vcs.getProject();
    myVcsHistoryCache = ProjectLevelVcsManager.getInstance(myProject).getVcsHistoryCache();
    myConfiguration = VcsConfiguration.getInstance(myProject);
    myHistoryProvider = historyProvider;
    myType = vcs.getType();
    myDiffProvider = diffProvider;
  }

  @CalledInAwt
  public void createSessionFor(@NotNull VcsKey vcsKey, @NotNull FilePath filePath, @NotNull Consumer<VcsHistorySession> continuation,
                               @NotNull VcsBackgroundableActions actionKey, boolean silent) {
    ThrowableComputable<VcsHistorySession, VcsException> throwableComputable;
    VcsCacheableHistorySessionFactory<Serializable, VcsAbstractHistorySession> factory = getCacheableFactory();
    if (factory != null) {
      throwableComputable = () -> {
        // we check for the last revision, since requests to this exact method at the moment only request history once, and no refresh is possible later
        VcsAbstractHistorySession session = getSessionFromCacheWithLastRevisionCheck(filePath, vcsKey, factory);
        if (session == null) {
          session = createSessionWithLimitCheck(filePath);
          FilePath correctedPath = factory.getUsedFilePath(session);
          myVcsHistoryCache.put(filePath, correctedPath, vcsKey, (VcsAbstractHistorySession)session.copy(), factory, true);
        }
        return session;
      };
    }
    else {
      throwableComputable = () -> createSessionWithLimitCheck(filePath);
    }

    String title = VcsBundle.message("loading.file.history.progress");
    String errorTitle = silent ? null : VcsBundle.message("message.title.could.not.load.file.history");

    BackgroundableActionLock lock = BackgroundableActionLock.getLock(myProject, actionKey, filePath.getPath());
    if (lock.isLocked()) return;

    VcsBackgroundableComputable<VcsHistorySession> computable =
      new VcsBackgroundableComputable<>(myProject, title, errorTitle, throwableComputable, continuation, lock);
    lock.lock();
    ProgressManager.getInstance().run(computable);
  }

  @CalledInAwt
  public void executeAppendableSession(@NotNull VcsKey vcsKey,
                                       @NotNull FilePath filePath,
                                       @NotNull VcsAppendableHistorySessionPartner partner,
                                       boolean canUseCache,
                                       boolean canUseLastRevisionCheck) {
    VcsCacheableHistorySessionFactory<Serializable, VcsAbstractHistorySession> cacheableFactory = getCacheableFactory();
    if (cacheableFactory != null && canUseCache) {
      VcsAbstractHistorySession session = getFullHistoryFromCache(vcsKey, filePath, cacheableFactory);
      if (session != null) {
        partner.reportCreatedEmptySession(session);
        partner.finished();
        return;
      }
    }

    BackgroundableActionLock lock =
      BackgroundableActionLock.getLock(myProject, VcsBackgroundableActions.CREATE_HISTORY_SESSION, filePath.getPath());
    if (lock.isLocked()) return;
    lock.lock();

    VcsAppendableHistorySessionPartner cachedPartner = partner;
    if (cacheableFactory != null) {
      cachedPartner = new HistoryPartnerProxy(partner, session -> {
        if (session == null) return;
        FilePath correctedPath = cacheableFactory.getUsedFilePath(session);
        myVcsHistoryCache.put(filePath, correctedPath, vcsKey, (VcsAbstractHistorySession)session.copy(), cacheableFactory, true);
      });
    }

    reportHistory(filePath, null, vcsKey, lock, cachedPartner, canUseLastRevisionCheck);
  }

  /**
   * @throws UnsupportedOperationException if this proxy was created for {@link VcsHistoryProvider} instance,
   *                                       that doesn't implement {@link VcsHistoryProviderEx}
   */
  @CalledInAwt
  public void executeAppendableSession(@NotNull VcsKey vcsKey, @NotNull FilePath filePath, @NotNull VcsRevisionNumber startRevisionNumber,
                                       @NotNull VcsAppendableHistorySessionPartner partner) {
    if (!(myHistoryProvider instanceof VcsHistoryProviderEx)) throw new UnsupportedOperationException();

    BackgroundableActionLock lock =
      BackgroundableActionLock.getLock(myProject, VcsBackgroundableActions.CREATE_HISTORY_SESSION, filePath.getPath());
    if (lock.isLocked()) return;
    lock.lock();

    reportHistory(filePath, startRevisionNumber, vcsKey, lock, partner, false);
  }

  @Nullable
  protected VcsCacheableHistorySessionFactory<Serializable, VcsAbstractHistorySession> getCacheableFactory() {
    if (!(myHistoryProvider instanceof VcsCacheableHistorySessionFactory)) return null;
    //noinspection unchecked
    return (VcsCacheableHistorySessionFactory<Serializable, VcsAbstractHistorySession>)myHistoryProvider;
  }

  private VcsAbstractHistorySession getFullHistoryFromCache(@NotNull VcsKey vcsKey,
                                                            @NotNull FilePath filePath,
                                                            @NotNull VcsCacheableHistorySessionFactory<Serializable, VcsAbstractHistorySession> cacheableFactory) {
    VcsAbstractHistorySession fullSession = myVcsHistoryCache.getFull(filePath, vcsKey, cacheableFactory);
    if (fullSession != null) {
      if (myConfiguration.LIMIT_HISTORY) {
        if (myConfiguration.MAXIMUM_HISTORY_ROWS < fullSession.getRevisionList().size()) {
          final List<VcsFileRevision> list = fullSession.getRevisionList();
          final List<VcsFileRevision> was = new ArrayList<>(list.subList(0, myConfiguration.MAXIMUM_HISTORY_ROWS));
          list.clear();
          list.addAll(was);
        }
      }
    }
    return fullSession;
  }

  private void reportHistory(@NotNull FilePath filePath, @Nullable VcsRevisionNumber startRevisionNumber,
                             @NotNull VcsKey vcsKey, @NotNull BackgroundableActionLock lock,
                             @NotNull VcsAppendableHistorySessionPartner partner, boolean canUseLastRevisionCheck) {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, VcsBundle.message("loading.file.history.progress"), true) {
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText(VcsUtil.getPathForProgressPresentation(filePath.getIOFile()));
        indicator.setIndeterminate(true);
        try {
          VcsAbstractHistorySession cachedSession = null;
          VcsCacheableHistorySessionFactory<Serializable, VcsAbstractHistorySession> cacheableFactory = getCacheableFactory();
          if (canUseLastRevisionCheck && cacheableFactory != null) {
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
        }
        catch (VcsException e) {
          partner.reportException(e);
        }
        finally {
          partner.finished();
          ApplicationManager.getApplication().invokeLater(lock::unlock, ModalityState.NON_MODAL);
        }
      }
    });
  }

  private VcsAbstractHistorySession createSessionWithLimitCheck(@NotNull FilePath filePath) throws VcsException {
    LimitHistoryCheck check = new LimitHistoryCheck(myProject, filePath.getPath());
    VcsAppendableHistoryPartnerAdapter partner = new VcsAppendableHistoryPartnerAdapter() {
      @Override
      public void acceptRevision(VcsFileRevision revision) {
        check.checkNumber();
        super.acceptRevision(revision);
      }
    };
    try {
      myHistoryProvider.reportAppendableHistory(filePath, partner);
    }
    catch (ProcessCanceledException e) {
      if (!check.isOver()) throw e;
    }
    return partner.getSession();
  }

  @Nullable
  private VcsAbstractHistorySession getSessionFromCacheWithLastRevisionCheck(@NotNull FilePath filePath,
                                                                             @NotNull VcsKey vcsKey,
                                                                             @NotNull VcsCacheableHistorySessionFactory<Serializable, VcsAbstractHistorySession> cacheableFactory) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText2("Checking last revision");
    }
    VcsAbstractHistorySession cached = getFullHistoryFromCache(vcsKey, filePath, cacheableFactory);
    if (cached == null) return null;

    FilePath correctedFilePath = cacheableFactory.getUsedFilePath(cached);
    FilePath path = correctedFilePath != null ? correctedFilePath : filePath;

    if (VcsType.distributed.equals(myType)) {
      VirtualFile virtualFile = path.getVirtualFile();
      if (virtualFile == null) {
        virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.getPath());
      }
      if (virtualFile != null) {
        VcsRevisionNumber currentRevision = myDiffProvider.getCurrentRevision(virtualFile);
        List<VcsFileRevision> revisionList = cached.getRevisionList();
        if (!revisionList.isEmpty() && revisionList.get(0).getRevisionNumber().equals(currentRevision)) {
          return cached;
        }
      }
      return null;
    }

    ItemLatestState lastRevision = myDiffProvider.getLastRevision(path);
    if (lastRevision != null && !lastRevision.isDefaultHead() && lastRevision.isItemExists()) {
      List<VcsFileRevision> revisionList = cached.getRevisionList();
      if (!revisionList.isEmpty() && revisionList.get(0).getRevisionNumber().equals(lastRevision.getNumber())) {
        return cached;
      }
    }
    return null;
  }

  private static class HistoryPartnerProxy implements VcsAppendableHistorySessionPartner {
    private final VcsAppendableHistorySessionPartner myPartner;
    private final Consumer<VcsAbstractHistorySession> myFinish;
    private VcsAbstractHistorySession myCopy;

    private HistoryPartnerProxy(VcsAppendableHistorySessionPartner partner, final Consumer<VcsAbstractHistorySession> finish) {
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

    @Override
    public void beforeRefresh() {
      myPartner.beforeRefresh();
    }

    @Override
    public void forceRefresh() {
      myPartner.forceRefresh();
    }
  }
}

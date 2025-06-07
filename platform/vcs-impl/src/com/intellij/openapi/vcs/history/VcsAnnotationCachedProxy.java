// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.VcsAnnotation;
import com.intellij.openapi.vcs.annotate.VcsCacheableAnnotationProvider;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.vcs.CacheableAnnotationProvider;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsAnnotationCachedProxy implements AnnotationProvider, CacheableAnnotationProvider {
  private final VcsHistoryCache myCache;
  private final AbstractVcs myVcs;
  private static final Logger LOG = Logger.getInstance(VcsAnnotationCachedProxy.class);
  private final AnnotationProvider myAnnotationProvider;

  public VcsAnnotationCachedProxy(@NotNull AbstractVcs vcs, @NotNull AnnotationProvider provider) {
    assert provider instanceof VcsCacheableAnnotationProvider;
    myVcs = vcs;
    myCache = ProjectLevelVcsManager.getInstance(vcs.getProject()).getVcsHistoryCache();
    myAnnotationProvider = provider;
  }

  @Override
  public FileAnnotation annotate(final VirtualFile file) throws VcsException {
    final DiffProvider diffProvider = myVcs.getDiffProvider();
    final VcsRevisionNumber currentRevision = diffProvider.getCurrentRevision(file);

    return annotate(file, currentRevision, true, () -> myAnnotationProvider.annotate(file));
  }

  @Override
  public FileAnnotation annotate(final VirtualFile file, final VcsFileRevision revision) throws VcsException {
    return annotate(file, revision.getRevisionNumber(), false, () -> myAnnotationProvider.annotate(file, revision));
  }

  @Override
  public void populateCache(@NotNull VirtualFile file) throws VcsException {
    annotate(file);
  }

  @Override
  public @Nullable FileAnnotation getFromCache(@NotNull VirtualFile file) {
    return null;
  }

  /**
   * @param currentRevision - just a hint for optimization
   */
  private FileAnnotation annotate(VirtualFile file, final VcsRevisionNumber revisionNumber, final boolean currentRevision,
                                  final ThrowableComputable<? extends FileAnnotation, VcsException> delegate) throws VcsException {

    final FilePath filePath = VcsUtil.getFilePath(file);

    final VcsCacheableAnnotationProvider cacheableAnnotationProvider = (VcsCacheableAnnotationProvider)myAnnotationProvider;

    VcsAnnotation vcsAnnotation = null;
    if (revisionNumber != null) {
      Object cachedData = myCache.getAnnotation(filePath, myVcs.getKeyInstanceMethod(), revisionNumber);
      vcsAnnotation = ObjectUtils.tryCast(cachedData, VcsAnnotation.class);
    }

    if (vcsAnnotation != null) {
      final VcsHistoryProvider historyProvider = myVcs.getVcsHistoryProvider();
      final VcsAbstractHistorySession history = getHistory(revisionNumber, filePath, historyProvider, vcsAnnotation.getFirstRevision());
      if (history == null) return null;
      // question is whether we need "not moved" path here?
      final ContentRevision fileContent = myVcs.getDiffProvider().createFileContent(revisionNumber, file);
      final FileAnnotation restored = cacheableAnnotationProvider.restore(vcsAnnotation, history, fileContent.getContent(), currentRevision,
                                                                          revisionNumber);
      if (restored != null) {
        return restored;
      }
    }

    final FileAnnotation fileAnnotation = delegate.compute();
    vcsAnnotation = cacheableAnnotationProvider.createCacheable(fileAnnotation);
    if (vcsAnnotation == null) return fileAnnotation;

    if (revisionNumber != null) {
      myCache.putAnnotation(filePath, myVcs.getKeyInstanceMethod(), revisionNumber, vcsAnnotation);
    }

    if (myVcs.getVcsHistoryProvider() instanceof VcsCacheableHistorySessionFactory) {
      loadHistoryInBackgroundToCache(revisionNumber, filePath, vcsAnnotation);
    }
    return fileAnnotation;
  }

  // todo will be removed - when annotation will be presented together with history
  private void loadHistoryInBackgroundToCache(final VcsRevisionNumber revisionNumber,
                                              final FilePath filePath,
                                              final VcsAnnotation vcsAnnotation) {
    BackgroundTaskUtil.executeOnPooledThread(myVcs.getProject(), () -> {
      try {
        getHistory(revisionNumber, filePath, myVcs.getVcsHistoryProvider(), vcsAnnotation.getFirstRevision());
      }
      catch (VcsException e) {
        LOG.info(e);
      }
    });
  }

  private VcsAbstractHistorySession getHistory(VcsRevisionNumber revision, FilePath filePath, VcsHistoryProvider historyProvider,
                                               final @Nullable VcsRevisionNumber firstRevision) throws VcsException {
    final boolean historyCacheSupported = historyProvider instanceof VcsCacheableHistorySessionFactory;
    if (historyCacheSupported) {
      final VcsCacheableHistorySessionFactory cacheableHistorySessionFactory = (VcsCacheableHistorySessionFactory)historyProvider;
      final VcsAbstractHistorySession cachedSession = myCache.getSession(filePath, myVcs.getKeyInstanceMethod(),
                                                                         cacheableHistorySessionFactory, true);
      if (cachedSession != null && !cachedSession.getRevisionList().isEmpty()) {
        final VcsFileRevision recentRevision = cachedSession.getRevisionList().get(0);
        if (recentRevision.getRevisionNumber().compareTo(revision) >= 0 && (firstRevision == null || cachedSession.getHistoryAsMap().containsKey(firstRevision))) {
          return cachedSession;
        }
      }
    }
    // history may be also cut
    final VcsAbstractHistorySession sessionFor;
    if (firstRevision != null) {
      sessionFor = limitedHistory(filePath, firstRevision);
    }
    else {
      sessionFor = (VcsAbstractHistorySession)historyProvider.createSessionFor(filePath);
    }
    if (sessionFor != null && historyCacheSupported) {
      final VcsCacheableHistorySessionFactory cacheableHistorySessionFactory = (VcsCacheableHistorySessionFactory)historyProvider;
      final FilePath correctedPath = cacheableHistorySessionFactory.getUsedFilePath(sessionFor);
      myCache.putSession(filePath, correctedPath, myVcs.getKeyInstanceMethod(), sessionFor, cacheableHistorySessionFactory, firstRevision == null);
    }
    return sessionFor;
  }

  @Override
  public boolean isAnnotationValid(@NotNull VcsFileRevision rev) {
    return myAnnotationProvider.isAnnotationValid(rev);
  }

  private VcsAbstractHistorySession limitedHistory(final FilePath filePath, final @NotNull VcsRevisionNumber firstNumber) throws VcsException {
    final VcsAbstractHistorySession[] result = new VcsAbstractHistorySession[1];
    final VcsException[] exc = new VcsException[1];

    try {
      myVcs.getVcsHistoryProvider().reportAppendableHistory(filePath, new VcsAppendableHistorySessionPartner() {
        @Override
        public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
          result[0] = session;
        }

        @Override
        public void acceptRevision(VcsFileRevision revision) {
          result[0].appendRevision(revision);
          if (firstNumber.equals(revision.getRevisionNumber())) throw new ProcessCanceledException();
        }

        @Override
        public void reportException(VcsException exception) {
          exc[0] = exception;
        }
      });
    }
    catch (ProcessCanceledException e) {
      // ok
    }
    if (exc[0] != null) {
      throw exc[0];
    }
    return result[0];
  }
}


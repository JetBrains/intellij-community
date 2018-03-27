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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
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
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author irengrig
 */
public class VcsAnnotationCachedProxy implements AnnotationProvider {
  private final VcsHistoryCache myCache;
  private final AbstractVcs myVcs;
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.history.VcsAnnotationCachedProxy");
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

    return annotate(file, currentRevision, true, new ThrowableComputable<FileAnnotation, VcsException>() {
      @Override
      public FileAnnotation compute() throws VcsException {
        return myAnnotationProvider.annotate(file);
      }
    });
  }

  @Override
  public FileAnnotation annotate(final VirtualFile file, final VcsFileRevision revision) throws VcsException {
    return annotate(file, revision.getRevisionNumber(), false, new ThrowableComputable<FileAnnotation, VcsException>() {
      @Override
      public FileAnnotation compute() throws VcsException {
        return myAnnotationProvider.annotate(file, revision);
      }
    });
  }

  @Override
  public boolean isCaching() {
    return true;
  }

  /**
   * @param currentRevision - just a hint for optimization
   */
  private FileAnnotation annotate(VirtualFile file, final VcsRevisionNumber revisionNumber, final boolean currentRevision,
                                  final ThrowableComputable<FileAnnotation, VcsException> delegate) throws VcsException {
    final AnnotationProvider annotationProvider = myAnnotationProvider;

    final FilePath filePath = VcsUtil.getFilePath(file);

    final VcsCacheableAnnotationProvider cacheableAnnotationProvider = (VcsCacheableAnnotationProvider)annotationProvider;

    VcsAnnotation vcsAnnotation = null;
    if (revisionNumber != null) {
      Object cachedData = myCache.get(filePath, myVcs.getKeyInstanceMethod(), revisionNumber);
      vcsAnnotation = ObjectUtils.tryCast(cachedData, VcsAnnotation.class);
    }

    if (vcsAnnotation != null) {
      final VcsHistoryProvider historyProvider = myVcs.getVcsHistoryProvider();
      final VcsAbstractHistorySession history = getHistory(revisionNumber, filePath, historyProvider, vcsAnnotation.getFirstRevision());
      if (history == null) return null;
      // question is whether we need "not moved" path here?
      final ContentRevision fileContent = myVcs.getDiffProvider().createFileContent(revisionNumber, file);
      final FileAnnotation restored = cacheableAnnotationProvider.
        restore(vcsAnnotation, history, fileContent.getContent(), currentRevision,
                                                                          revisionNumber);
      if (restored != null) {
        return restored;
      }
    }

    final FileAnnotation fileAnnotation = delegate.compute();
    vcsAnnotation = cacheableAnnotationProvider.createCacheable(fileAnnotation);
    if (vcsAnnotation == null) return fileAnnotation;

    if (revisionNumber != null) {
      myCache.put(filePath, myVcs.getKeyInstanceMethod(), revisionNumber, vcsAnnotation);
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
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        getHistory(revisionNumber, filePath, myVcs.getVcsHistoryProvider(), vcsAnnotation.getFirstRevision());
      }
      catch (VcsException e) {
        LOG.info(e);
      }
    });
  }

  private VcsAbstractHistorySession getHistory(VcsRevisionNumber revision, FilePath filePath, VcsHistoryProvider historyProvider,
                                               @Nullable final VcsRevisionNumber firstRevision) throws VcsException {
    final boolean historyCacheSupported = historyProvider instanceof VcsCacheableHistorySessionFactory;
    if (historyCacheSupported) {
      final VcsCacheableHistorySessionFactory cacheableHistorySessionFactory = (VcsCacheableHistorySessionFactory)historyProvider;
      final VcsAbstractHistorySession cachedSession =
        myCache.getMaybePartial(filePath, myVcs.getKeyInstanceMethod(), cacheableHistorySessionFactory);
      if (cachedSession != null && ! cachedSession.getRevisionList().isEmpty()) {
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
    } else {
      sessionFor = (VcsAbstractHistorySession) historyProvider.createSessionFor(filePath);
    }
    if (sessionFor != null && historyCacheSupported) {
      final VcsCacheableHistorySessionFactory cacheableHistorySessionFactory = (VcsCacheableHistorySessionFactory)historyProvider;
      final FilePath correctedPath = cacheableHistorySessionFactory.getUsedFilePath(sessionFor);
      myCache.put(filePath, correctedPath, myVcs.getKeyInstanceMethod(), sessionFor, cacheableHistorySessionFactory, firstRevision == null);
    }
    return sessionFor;
  }

  @Override
  public boolean isAnnotationValid(@NotNull VcsFileRevision rev) {
    return myAnnotationProvider.isAnnotationValid(rev);
  }

  private VcsAbstractHistorySession limitedHistory(final FilePath filePath, @NotNull final VcsRevisionNumber firstNumber) throws VcsException {
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

        @Override
        public void finished() {
        }

        @Override
        public void beforeRefresh() {
        }

        @Override
        public void forceRefresh() {
        }
      });
    } catch (ProcessCanceledException e) {
     // ok
    }
    if (exc[0] != null) {
      throw exc[0];
    }
    return result[0];
  }
}


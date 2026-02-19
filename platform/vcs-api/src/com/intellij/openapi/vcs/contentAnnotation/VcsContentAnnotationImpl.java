// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.contentAnnotation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.diff.DiffMixin;
import com.intellij.openapi.vcs.history.VcsRevisionDescription;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public final class VcsContentAnnotationImpl implements VcsContentAnnotation {
  private final Project myProject;
  private final VcsContentAnnotationSettings mySettings;
  private final ContentAnnotationCache myContentAnnotationCache;
  private static final Logger LOG = Logger.getInstance(VcsContentAnnotationImpl.class);

  public static VcsContentAnnotation getInstance(final Project project) {
    return project.getService(VcsContentAnnotation.class);
  }

  public VcsContentAnnotationImpl(Project project) {
    myProject = project;
    mySettings = VcsContentAnnotationSettings.getInstance(project);
    myContentAnnotationCache = project.getService(ContentAnnotationCache.class);
  }

  @Override
  public @Nullable VcsRevisionNumber fileRecentlyChanged(VirtualFile vf) {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    final AbstractVcs vcs = vcsManager.getVcsFor(vf);
    if (vcs == null) return null;
    if (vcs.getDiffProvider() instanceof DiffMixin) {
      final VcsRevisionDescription description = ((DiffMixin)vcs.getDiffProvider()).getCurrentRevisionDescription(vf);
      if (description == null) return null;
      final Date date = description.getRevisionDate();
      return isRecent(date) ? description.getRevisionNumber() : null;
    }
    return null;
  }

  private boolean isRecent(Date date) {
    return date.getTime() > (System.currentTimeMillis() - mySettings.getLimit());
  }

  @Override
  public boolean intervalRecentlyChanged(VirtualFile file, TextRange lineInterval, VcsRevisionNumber currentRevisionNumber) {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    final AbstractVcs vcs = vcsManager.getVcsFor(file);
    if (vcs == null || vcs.getDiffProvider() == null) return false;
    if (currentRevisionNumber == null) {
      currentRevisionNumber = vcs.getDiffProvider().getCurrentRevision(file);
      assert currentRevisionNumber != null;
    }
    final ThreeState isRecent = myContentAnnotationCache.isRecent(file, vcs.getKeyInstanceMethod(), currentRevisionNumber, lineInterval,
                                                          System.currentTimeMillis() - mySettings.getLimit());
    if (! ThreeState.UNSURE.equals(isRecent)) return ThreeState.YES.equals(isRecent);

    final FileAnnotation fileAnnotation;
    try {
      fileAnnotation = vcs.getAnnotationProvider().annotate(file);
    }
    catch (VcsException e) {
      LOG.info(e);
      return false;
    }
    myContentAnnotationCache.register(file, vcs.getKeyInstanceMethod(), currentRevisionNumber, fileAnnotation);
    for (int i = lineInterval.getStartOffset(); i <= lineInterval.getEndOffset(); i++) {
      Date lineDate = fileAnnotation.getLineDate(i);
      if (lineDate != null && isRecent(lineDate)) return true;
    }
    return false;
  }
}

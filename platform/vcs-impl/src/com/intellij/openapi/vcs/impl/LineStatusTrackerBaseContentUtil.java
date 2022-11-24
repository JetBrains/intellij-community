// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LineStatusTrackerBaseContentUtil {
  private static final Logger LOG = Logger.getInstance(LineStatusTrackerBaseContentUtil.class);

  @Nullable
  private static VcsBaseContentProvider findProviderFor(@NotNull Project project, @NotNull VirtualFile file) {
    return VcsBaseContentProvider.EP_NAME.findFirstSafe(project, it -> it.isSupported(file));
  }

  public static boolean isSupported(@NotNull Project project, @NotNull VirtualFile file) {
    return isHandledByVcs(project, file) || findProviderFor(project, file) != null;
  }

  private static boolean isHandledByVcs(@NotNull Project project, @NotNull VirtualFile file) {
    return file.isInLocalFileSystem() && ProjectLevelVcsManager.getInstance(project).getVcsFor(file) != null;
  }

  @Nullable
  public static VcsBaseContentProvider.BaseContent getBaseRevision(@NotNull Project project, @NotNull final VirtualFile file) {
    if (!isHandledByVcs(project, file)) {
      VcsBaseContentProvider provider = findProviderFor(project, file);
      return provider == null ? null : provider.getBaseRevision(file);
    }

    ChangeListManager changeListManager = ChangeListManager.getInstance(project);

    Change change = changeListManager.getChange(file);
    if (change != null) {
      ContentRevision beforeRevision = change.getBeforeRevision();
      return beforeRevision == null ? null : createBaseContent(project, beforeRevision);
    }

    FileStatus status = changeListManager.getStatus(file);
    if (status == FileStatus.HIJACKED) {
      AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
      DiffProvider diffProvider = vcs != null ? vcs.getDiffProvider() : null;
      if (diffProvider != null) {
        VcsRevisionNumber currentRevision = diffProvider.getCurrentRevision(file);
        return currentRevision == null ? null : new HijackedBaseContent(project, diffProvider, file, currentRevision);
      }
    }

    if (status == FileStatus.NOT_CHANGED) {
      AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
      DiffProvider diffProvider = vcs != null ? vcs.getDiffProvider() : null;
      ChangeProvider cp = vcs != null ? vcs.getChangeProvider() : null;
      if (diffProvider != null && cp != null) {
        if (cp.isModifiedDocumentTrackingRequired() &&
            FileDocumentManager.getInstance().isFileModified(file)) {
          ContentRevision beforeRevision = diffProvider.createCurrentFileContent(file);
          if (beforeRevision != null) return createBaseContent(project, beforeRevision);
        }
      }
    }

    return null;
  }

  @NotNull
  public static VcsBaseContentProvider.BaseContent createBaseContent(@NotNull Project project, @NotNull ContentRevision contentRevision) {
    return new BaseContentImpl(project, contentRevision);
  }

  private static class BaseContentImpl implements VcsBaseContentProvider.BaseContent {
    @NotNull private final Project myProject;
    @NotNull private final ContentRevision myContentRevision;

    BaseContentImpl(@NotNull Project project, @NotNull ContentRevision contentRevision) {
      myProject = project;
      myContentRevision = contentRevision;
    }

    @NotNull
    @Override
    public VcsRevisionNumber getRevisionNumber() {
      return myContentRevision.getRevisionNumber();
    }

    @Nullable
    @Override
    public String loadContent() {
      return loadContentRevision(myProject, myContentRevision);
    }
  }

  private static class HijackedBaseContent implements VcsBaseContentProvider.BaseContent {
    @Nullable private final Project myProject;
    @NotNull private final DiffProvider myDiffProvider;
    @NotNull private final VirtualFile myFile;
    @NotNull private final VcsRevisionNumber myRevision;

    HijackedBaseContent(@Nullable Project project,
                        @NotNull DiffProvider diffProvider,
                        @NotNull VirtualFile file,
                        @NotNull VcsRevisionNumber revision) {
      myProject = project;
      myDiffProvider = diffProvider;
      myFile = file;
      myRevision = revision;
    }

    @NotNull
    @Override
    public VcsRevisionNumber getRevisionNumber() {
      return myRevision;
    }

    @Nullable
    @Override
    public String loadContent() {
      ContentRevision contentRevision = myDiffProvider.createFileContent(myRevision, myFile);
      if (contentRevision == null) return null;
      return loadContentRevision(myProject, contentRevision);
    }
  }

  @Nullable
  private static String loadContentRevision(@Nullable Project project, @NotNull ContentRevision contentRevision) {
    try {
      if (contentRevision instanceof ByteBackedContentRevision) {
        byte[] revisionContent = ((ByteBackedContentRevision)contentRevision).getContentAsBytes();
        FilePath filePath = contentRevision.getFile();

        if (revisionContent != null) {
          return VcsImplUtil.loadTextFromBytes(project, revisionContent, filePath);
        }
        else {
          return null;
        }
      }
      else {
        return contentRevision.getContent();
      }
    }
    catch (VcsException ex) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(ex);
      }
      return null;
    }
  }
}

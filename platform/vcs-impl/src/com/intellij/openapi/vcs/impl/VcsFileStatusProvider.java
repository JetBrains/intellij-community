// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import com.intellij.vcsUtil.VcsImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service
public final class VcsFileStatusProvider implements FileStatusProvider, VcsBaseContentProvider {
  private final Project myProject;

  private static final Logger LOG = Logger.getInstance(VcsFileStatusProvider.class);

  public static VcsFileStatusProvider getInstance(@NotNull Project project) {
    return project.getService(VcsFileStatusProvider.class);
  }

  VcsFileStatusProvider(@NotNull Project project) {
    myProject = project;

    project.getMessageBus().connect().subscribe(ChangeListListener.TOPIC, new ChangeListAdapter() {
      @Override
      public void changeListAdded(ChangeList list) {
        fileStatusesChanged();
      }

      @Override
      public void changeListRemoved(ChangeList list) {
        fileStatusesChanged();
      }

      @Override
      public void changeListUpdateDone() {
        fileStatusesChanged();
      }
    });

    VcsBaseContentProvider.EP_NAME.addChangeListener(myProject, this::fileStatusesChanged, myProject);
  }

  private void fileStatusesChanged() {
    FileStatusManager.getInstance(myProject).fileStatusesChanged();
  }

  @Override
  @NotNull
  public FileStatus getFileStatus(@NotNull final VirtualFile virtualFile) {
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(virtualFile);
    if (vcs == null) {
      if (ScratchUtil.isScratch(virtualFile)) {
        return FileStatus.SUPPRESSED;
      }
      return FileStatusManagerImpl.getDefaultStatus(virtualFile);
    }

    final FileStatus status = ChangeListManager.getInstance(myProject).getStatus(virtualFile);
    if (status == FileStatus.NOT_CHANGED && isDocumentModified(virtualFile)) {
      return FileStatus.MODIFIED;
    }
    if (status == FileStatus.NOT_CHANGED) {
      return FileStatusManagerImpl.getDefaultStatus(virtualFile);
    }
    return status;
  }

  private static boolean isDocumentModified(VirtualFile virtualFile) {
    if (virtualFile.isDirectory()) return false;
    return FileDocumentManager.getInstance().isFileModified(virtualFile);
  }

  public void refreshFileStatusFromDocument(@NotNull final VirtualFile virtualFile, @NotNull final Document doc) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("refreshFileStatusFromDocument: file.getModificationStamp()=" + virtualFile.getModificationStamp() +
                ", document.getModificationStamp()=" + doc.getModificationStamp());
    }

    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(virtualFile);
    if (vcs == null) return;

    FileStatusManagerImpl fileStatusManager = (FileStatusManagerImpl)FileStatusManager.getInstance(myProject);
    FileStatus cachedStatus = fileStatusManager.getCachedStatus(virtualFile);
    boolean isDocumentModified = isDocumentModified(virtualFile);

    if (cachedStatus == FileStatus.MODIFIED && !isDocumentModified) {
      if (!((ReadonlyStatusHandlerImpl)ReadonlyStatusHandler.getInstance(myProject)).getState().SHOW_DIALOG) {
        RollbackEnvironment rollbackEnvironment = vcs.getRollbackEnvironment();
        if (rollbackEnvironment != null) {
          rollbackEnvironment.rollbackIfUnchanged(virtualFile);
        }
      }
    }

    boolean isStatusChanged = cachedStatus != null && cachedStatus != FileStatus.NOT_CHANGED;
    if (isStatusChanged != isDocumentModified) {
      fileStatusManager.fileStatusChanged(virtualFile);
    }

    ChangeProvider cp = vcs.getChangeProvider();
    if (cp != null && cp.isModifiedDocumentTrackingRequired()) {
      FileStatus status = ChangeListManager.getInstance(myProject).getStatus(virtualFile);
      boolean isClmStatusChanged = status != FileStatus.NOT_CHANGED;
      if (isClmStatusChanged != isDocumentModified) {
        VcsDirtyScopeManager.getInstance(myProject).fileDirty(virtualFile);
      }
    }
  }

  @NotNull
  public ThreeState getNotChangedDirectoryParentingStatus(@NotNull VirtualFile virtualFile) {
    if (VcsConfiguration.getInstance(myProject).SHOW_DIRTY_RECURSIVELY) {
      return ChangeListManager.getInstance(myProject).haveChangesUnder(virtualFile);
    }
    else {
      return ThreeState.NO;
    }
  }

  @Override
  @Nullable
  public BaseContent getBaseRevision(@NotNull final VirtualFile file) {
    if (!isHandledByVcs(file)) {
      VcsBaseContentProvider provider = findProviderFor(file);
      return provider == null ? null : provider.getBaseRevision(file);
    }

    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);

    Change change = changeListManager.getChange(file);
    if (change != null) {
      ContentRevision beforeRevision = change.getBeforeRevision();
      return beforeRevision == null ? null : createBaseContent(myProject, beforeRevision);
    }

    FileStatus status = changeListManager.getStatus(file);
    if (status == FileStatus.HIJACKED) {
      AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
      DiffProvider diffProvider = vcs != null ? vcs.getDiffProvider() : null;
      if (diffProvider != null) {
        VcsRevisionNumber currentRevision = diffProvider.getCurrentRevision(file);
        return currentRevision == null ? null : new HijackedBaseContent(myProject, diffProvider, file, currentRevision);
      }
    }

    if (status == FileStatus.NOT_CHANGED) {
      AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
      DiffProvider diffProvider = vcs != null ? vcs.getDiffProvider() : null;
      ChangeProvider cp = vcs != null ? vcs.getChangeProvider() : null;
      if (diffProvider != null && cp != null) {
        if (cp.isModifiedDocumentTrackingRequired() &&
            FileDocumentManager.getInstance().isFileModified(file)) {
          ContentRevision beforeRevision = diffProvider.createCurrentFileContent(file);
          if (beforeRevision != null) return createBaseContent(myProject, beforeRevision);
        }
      }
    }

    return null;
  }

  @Nullable
  private VcsBaseContentProvider findProviderFor(@NotNull VirtualFile file) {
    return VcsBaseContentProvider.EP_NAME.findFirstSafe(myProject, it -> it.isSupported(file));
  }

  @Override
  public boolean isSupported(@NotNull VirtualFile file) {
    return isHandledByVcs(file) || findProviderFor(file) != null;
  }

  private boolean isHandledByVcs(@NotNull VirtualFile file) {
    return file.isInLocalFileSystem() && ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file) != null;
  }

  @NotNull
  public static BaseContent createBaseContent(@NotNull Project project, @NotNull ContentRevision contentRevision) {
    return new BaseContentImpl(project, contentRevision);
  }

  private static class BaseContentImpl implements BaseContent {
    @Nullable private final Project myProject;
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

  private static class HijackedBaseContent implements BaseContent {
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
      return null;
    }
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.diff.DiffContentFactoryImpl;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

@Service
public final class VcsFileStatusProvider implements FileStatusProvider, VcsBaseContentProvider {
  private final Project myProject;
  private final ExtensionPointImpl<VcsBaseContentProvider> myAdditionalProviderPoint;

  private static final Logger LOG = Logger.getInstance(VcsFileStatusProvider.class);

  public static VcsFileStatusProvider getInstance(@NotNull Project project) {
    return project.getService(VcsFileStatusProvider.class);
  }

  VcsFileStatusProvider(@NotNull Project project) {
    myProject = project;
    myAdditionalProviderPoint = (ExtensionPointImpl<VcsBaseContentProvider>)VcsBaseContentProvider.EP_NAME.getPoint(project);

    ChangeListManager.getInstance(project).addChangeListListener(new ChangeListAdapter() {
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

  @Override
  public void refreshFileStatusFromDocument(@NotNull final VirtualFile virtualFile, @NotNull final Document doc) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("refreshFileStatusFromDocument: file.getModificationStamp()=" + virtualFile.getModificationStamp() + ", document.getModificationStamp()=" + doc.getModificationStamp());
    }
    FileStatusManagerImpl fileStatusManager = (FileStatusManagerImpl)FileStatusManager.getInstance(myProject);
    FileStatus cachedStatus = fileStatusManager.getCachedStatus(virtualFile);
    if (cachedStatus == null || cachedStatus == FileStatus.NOT_CHANGED || !isDocumentModified(virtualFile)) {
      AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(virtualFile);
      if (vcs == null) return;
      if (cachedStatus == FileStatus.MODIFIED && !isDocumentModified(virtualFile)) {
        if (!((ReadonlyStatusHandlerImpl)ReadonlyStatusHandler.getInstance(myProject)).getState().SHOW_DIALOG) {
          RollbackEnvironment rollbackEnvironment = vcs.getRollbackEnvironment();
          if (rollbackEnvironment != null) {
            rollbackEnvironment.rollbackIfUnchanged(virtualFile);
          }
        }
      }
      fileStatusManager.fileStatusChanged(virtualFile);
      ChangeProvider cp = vcs.getChangeProvider();
      if (cp != null && cp.isModifiedDocumentTrackingRequired()) {
        VcsDirtyScopeManager.getInstance(myProject).fileDirty(virtualFile);
      }
    }
  }

  @NotNull
  @Override
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
      return beforeRevision == null ? null : new BaseContentImpl(beforeRevision);
    }

    FileStatus status = changeListManager.getStatus(file);
    if (status == FileStatus.HIJACKED) {
      AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
      DiffProvider diffProvider = vcs != null ? vcs.getDiffProvider() : null;
      if (diffProvider != null) {
        VcsRevisionNumber currentRevision = diffProvider.getCurrentRevision(file);
        return currentRevision == null ? null : new HijackedBaseContent(diffProvider, file, currentRevision);
      }
    }

    return null;
  }

  @Nullable
  private VcsBaseContentProvider findProviderFor(@NotNull VirtualFile file) {
    for (VcsBaseContentProvider support : myAdditionalProviderPoint) {
      if (support == null) {
        break;
      }

      if (support.isSupported(file)) {
        return support;
      }
    }
    return null;
  }

  @Override
  public boolean isSupported(@NotNull VirtualFile file) {
    return isHandledByVcs(file) || findProviderFor(file) != null;
  }

  private boolean isHandledByVcs(@NotNull VirtualFile file) {
    return file.isInLocalFileSystem() && ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file) != null;
  }

  private static class BaseContentImpl implements BaseContent {
    @NotNull private final ContentRevision myContentRevision;

    BaseContentImpl(@NotNull ContentRevision contentRevision) {
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
      return loadContentRevision(myContentRevision);
    }
  }

  private static class HijackedBaseContent implements BaseContent {
    @NotNull private final DiffProvider myDiffProvider;
    @NotNull private final VirtualFile myFile;
    @NotNull private final VcsRevisionNumber myRevision;

    HijackedBaseContent(@NotNull DiffProvider diffProvider,
                        @NotNull VirtualFile file,
                        @NotNull VcsRevisionNumber revision) {
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
      return loadContentRevision(contentRevision);
    }
  }

  @Nullable
  private static String loadContentRevision(@NotNull ContentRevision contentRevision) {
    try {
      if (contentRevision instanceof ByteBackedContentRevision) {
        byte[] revisionContent = ((ByteBackedContentRevision)contentRevision).getContentAsBytes();
        FilePath filePath = contentRevision.getFile();

        if (revisionContent != null) {
          Charset charset = DiffContentFactoryImpl.guessCharset(revisionContent, filePath);
          return CharsetToolkit.decodeString(revisionContent, charset);
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

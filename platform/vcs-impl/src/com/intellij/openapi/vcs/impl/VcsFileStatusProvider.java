/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl;

import com.intellij.diff.DiffContentFactoryImpl;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * @author yole
 */
public class VcsFileStatusProvider implements FileStatusProvider, VcsBaseContentProvider {
  private final Project myProject;
  private final FileStatusManagerImpl myFileStatusManager;
  private final ProjectLevelVcsManager myVcsManager;
  private final ChangeListManager myChangeListManager;
  private final VcsDirtyScopeManager myDirtyScopeManager;
  private final VcsConfiguration myConfiguration;
  private final VcsBaseContentProvider[] myAdditionalProviders;
  private boolean myHaveEmptyContentRevisions;

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.VcsFileStatusProvider");

  public VcsFileStatusProvider(final Project project,
                               final FileStatusManagerImpl fileStatusManager,
                               final ProjectLevelVcsManager vcsManager,
                               ChangeListManager changeListManager,
                               VcsDirtyScopeManager dirtyScopeManager, VcsConfiguration configuration) {
    myProject = project;
    myFileStatusManager = fileStatusManager;
    myVcsManager = vcsManager;
    myChangeListManager = changeListManager;
    myDirtyScopeManager = dirtyScopeManager;
    myConfiguration = configuration;
    myHaveEmptyContentRevisions = true;
    myFileStatusManager.setFileStatusProvider(this);
    myAdditionalProviders = VcsBaseContentProvider.EP_NAME.getExtensions(project);

    changeListManager.addChangeListListener(new ChangeListAdapter() {
      @Override
      public void changeListAdded(ChangeList list) {
        fileStatusesChanged();
      }

      @Override
      public void changeListRemoved(ChangeList list) {
        fileStatusesChanged();
      }

      @Override
      public void changeListChanged(ChangeList list) {
        fileStatusesChanged();
      }

      @Override
      public void changeListUpdateDone() {
        if (myHaveEmptyContentRevisions) {
          myHaveEmptyContentRevisions = false;
          fileStatusesChanged();
        }
      }

      @Override public void unchangedFileStatusChanged() {
        fileStatusesChanged();
      }
    });
  }

  private void fileStatusesChanged() {
    myFileStatusManager.fileStatusesChanged();
  }

  @Override
  @NotNull
  public FileStatus getFileStatus(@NotNull final VirtualFile virtualFile) {
    final AbstractVcs vcs = myVcsManager.getVcsFor(virtualFile);
    if (vcs == null) {
      if (ScratchUtil.isScratch(virtualFile)) {
        return FileStatus.SUPPRESSED;
      }
      return FileStatusManagerImpl.getDefaultStatus(virtualFile);
    }

    final FileStatus status = myChangeListManager.getStatus(virtualFile);
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
    FileStatus cachedStatus = myFileStatusManager.getCachedStatus(virtualFile);
    if (cachedStatus == null || cachedStatus == FileStatus.NOT_CHANGED || !isDocumentModified(virtualFile)) {
      final AbstractVcs vcs = myVcsManager.getVcsFor(virtualFile);
      if (vcs == null) return;
      if (cachedStatus == FileStatus.MODIFIED && !isDocumentModified(virtualFile)) {
        if (!((ReadonlyStatusHandlerImpl) ReadonlyStatusHandlerImpl.getInstance(myProject)).getState().SHOW_DIALOG) {
          RollbackEnvironment rollbackEnvironment = vcs.getRollbackEnvironment();
          if (rollbackEnvironment != null) {
            rollbackEnvironment.rollbackIfUnchanged(virtualFile);
          }
        }
      }
      myFileStatusManager.fileStatusChanged(virtualFile);
      ChangeProvider cp = vcs.getChangeProvider();
      if (cp != null && cp.isModifiedDocumentTrackingRequired()) {
        myDirtyScopeManager.fileDirty(virtualFile);
      }
    }
  }

  @NotNull
  @Override
  public ThreeState getNotChangedDirectoryParentingStatus(@NotNull VirtualFile virtualFile) {
    return myConfiguration.SHOW_DIRTY_RECURSIVELY ? myChangeListManager.haveChangesUnder(virtualFile) : ThreeState.NO;
  }

  @Override
  @Nullable
  public BaseContent getBaseRevision(@NotNull final VirtualFile file) {
    if (!isHandledByVcs(file)) {
      VcsBaseContentProvider provider = findProviderFor(file);
      return provider == null ? null : provider.getBaseRevision(file);
    }
    final Change change = ChangeListManager.getInstance(myProject).getChange(file);
    if (change == null) return null;
    final ContentRevision beforeRevision = change.getBeforeRevision();
    if (beforeRevision == null) return null;
    if (beforeRevision instanceof BinaryContentRevision) return null;
    return new BaseContentImpl(beforeRevision);
  }

  @Nullable
  private VcsBaseContentProvider findProviderFor(@NotNull VirtualFile file) {
    for (VcsBaseContentProvider support : myAdditionalProviders) {
      if (support.isSupported(file)) return support;
    }
    return null;
  }

  @Override
  public boolean isSupported(@NotNull VirtualFile file) {
    return isHandledByVcs(file) || findProviderFor(file) != null;
  }

  private boolean isHandledByVcs(@NotNull VirtualFile file) {
    return file.isInLocalFileSystem() && myVcsManager.getVcsFor(file) != null;
  }

  private class BaseContentImpl implements BaseContent {
    @NotNull private final ContentRevision myContentRevision;

    public BaseContentImpl(@NotNull ContentRevision contentRevision) {
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
      String content;
      try {
        if (myContentRevision instanceof ByteBackedContentRevision) {
          byte[] revisionContent = ((ByteBackedContentRevision)myContentRevision).getContentAsBytes();
          FilePath filePath = myContentRevision.getFile();

          if (revisionContent != null) {
            Charset charset = DiffContentFactoryImpl.guessCharset(revisionContent, filePath);
            content = CharsetToolkit.decodeString(revisionContent, charset);
          }
          else {
            content = null;
          }
        }
        else {
          content = myContentRevision.getContent();
        }
      }
      catch (VcsException ex) {
        content = null;
      }
      if (content == null) {
        myHaveEmptyContentRevisions = true;
        return null;
      }
      return content;
    }
  }
}

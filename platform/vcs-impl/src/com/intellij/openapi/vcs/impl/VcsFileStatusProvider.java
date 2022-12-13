// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

@Service
public final class VcsFileStatusProvider {
  private final Project myProject;

  private static final Logger LOG = Logger.getInstance(VcsFileStatusProvider.class);

  public static VcsFileStatusProvider getInstance(@NotNull Project project) {
    return project.getService(VcsFileStatusProvider.class);
  }

  VcsFileStatusProvider(@NotNull Project project) {
    myProject = project;
  }

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
}
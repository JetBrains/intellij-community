/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author yole
 */
public class VcsFileStatusProvider implements FileStatusProvider {
  private final Project myProject;
  private final FileStatusManagerImpl myFileStatusManager;
  private final ProjectLevelVcsManager myVcsManager;
  private final ChangeListManager myChangeListManager;
  private final VcsDirtyScopeManager myDirtyScopeManager;
  private boolean myHaveEmptyContentRevisions;

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.VcsFileStatusProvider");

  public VcsFileStatusProvider(final Project project,
                               final FileStatusManagerImpl fileStatusManager,
                               final ProjectLevelVcsManager vcsManager,
                               ChangeListManager changeListManager,
                               VcsDirtyScopeManager dirtyScopeManager) {
    myProject = project;
    myFileStatusManager = fileStatusManager;
    myVcsManager = vcsManager;
    myChangeListManager = changeListManager;
    myDirtyScopeManager = dirtyScopeManager;
    myHaveEmptyContentRevisions = true;
    myFileStatusManager.setFileStatusProvider(this);

    changeListManager.addChangeListListener(new ChangeListAdapter() {
      public void changeListAdded(ChangeList list) {
        fileStatusesChanged();
      }

      public void changeListRemoved(ChangeList list) {
        fileStatusesChanged();
      }

      public void changeListChanged(ChangeList list) {
        fileStatusesChanged();
      }

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

  public FileStatus getFileStatus(final VirtualFile virtualFile) {
    final AbstractVcs vcs = myVcsManager.getVcsFor(virtualFile);
    if (vcs == null) return FileStatus.NOT_CHANGED;

    final FileStatus status = myChangeListManager.getStatus(virtualFile);
    if (status == FileStatus.NOT_CHANGED && isDocumentModified(virtualFile)) return FileStatus.MODIFIED;
    return status;
  }

  private static boolean isDocumentModified(VirtualFile virtualFile) {
    if (virtualFile.isDirectory()) return false;
    final FileDocumentManager fdm = FileDocumentManager.getInstance();
    final Document editorDocument = fdm.getCachedDocument(virtualFile);

    if (editorDocument != null && editorDocument.getModificationStamp() != virtualFile.getModificationStamp()) {
      return fdm.isDocumentUnsaved(editorDocument);
    }

    return false;
  }

  public void refreshFileStatusFromDocument(final VirtualFile file, final Document doc) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("refreshFileStatusFromDocument: file.getModificationStamp()=" + file.getModificationStamp() + ", document.getModificationStamp()=" + doc.getModificationStamp());
    }
    FileStatus cachedStatus = myFileStatusManager.getCachedStatus(file);
    if (cachedStatus == FileStatus.NOT_CHANGED || file.getModificationStamp() == doc.getModificationStamp()) {
      final AbstractVcs vcs = myVcsManager.getVcsFor(file);
      if (vcs == null) return;
      if (cachedStatus == FileStatus.MODIFIED && file.getModificationStamp() == doc.getModificationStamp()) {
        if (!((ReadonlyStatusHandlerImpl) ReadonlyStatusHandlerImpl.getInstance(myProject)).getState().SHOW_DIALOG) {
          RollbackEnvironment rollbackEnvironment = vcs.getRollbackEnvironment();
          if (rollbackEnvironment != null) {
            rollbackEnvironment.rollbackIfUnchanged(file);
          }
        }
      }
      myFileStatusManager.fileStatusChanged(file);
      ChangeProvider cp = vcs.getChangeProvider();
      if (cp != null && cp.isModifiedDocumentTrackingRequired()) {
        myDirtyScopeManager.fileDirty(file);
      }
    }
  }

  @Nullable
  String getBaseVersionContent(final VirtualFile file) {
    final Change change = ChangeListManager.getInstance(myProject).getChange(file);
    if (change != null) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      if (beforeRevision instanceof BinaryContentRevision) {
        return null;
      }
      if (beforeRevision != null) {
        String content;
        try {
          content = beforeRevision.getContent();
        }
        catch(VcsException ex) {
          content = null;
        }
        if (content == null) myHaveEmptyContentRevisions = true;
        return content;
      }
      return null;
    }

    final Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (document != null && document.getModificationStamp() != file.getModificationStamp()) {
      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        public String compute() {
          return LoadTextUtil.loadText(file).toString();
        }
      });
    }

    return null;
  }
}

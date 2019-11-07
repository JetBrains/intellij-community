/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.checkinProject;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.actions.RestoreFileAction;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextAdapter;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.rollback.DefaultRollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.util.List;

/**
 * @author yole
 */
public class CvsRollbackEnvironment extends DefaultRollbackEnvironment {

  private static final Logger LOG = Logger.getInstance(CvsRollbackEnvironment.class);
  private final Project myProject;

  public CvsRollbackEnvironment(final Project project) {
    myProject = project;
  }

  @Override
  public void rollbackChanges(List<Change> changes, final List<VcsException> exceptions,
                              @NotNull final RollbackProgressListener listener) {
    listener.determinate();
    for (Change change : changes) {
      final FilePath filePath = ChangesUtil.getFilePath(change);
      listener.accept(change);
      final VirtualFile parent = filePath.getVirtualFileParent();
      final String name = filePath.getName();

      switch (change.getType()) {
        case DELETED:
          restoreFile(parent, name);
          break;

        case MODIFICATION:
          restoreFile(parent, name);
          break;

        case MOVED:
          CvsUtil.removeEntryFor(CvsVfsUtil.getFileFor(parent, name));
          break;

        case NEW:
          CvsUtil.removeEntryFor(CvsVfsUtil.getFileFor(parent, name));
          break;
      }
    }
  }

  @Override
  public void rollbackMissingFileDeletion(List<FilePath> filePaths, final List<VcsException> exceptions,
                                          final RollbackProgressListener listener) {
    final CvsHandler cvsHandler = CommandCvsHandler.createCheckoutFileHandler(filePaths.toArray(new FilePath[0]),
                                                                              CvsConfiguration.getInstance(myProject), null);
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(cvsHandler, CvsOperationExecutorCallback.EMPTY);
  }

  private void restoreFile(final VirtualFile parent, String name) {
    if (restoreFileFromCache(parent, name)) {
      return;
    }
    try {
      new RestoreFileAction(parent, name).actionPerformed(new CvsContextAdapter() {
        @Override
        public Project getProject() {
          return myProject;
        }
      });
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private boolean restoreFileFromCache(VirtualFile parent, String name) {
    final Entry entry = CvsEntriesManager.getInstance().getEntryFor(parent, name);
    final String revision = entry.getRevision();
    if (revision == null) {
      return false;
    }
    final boolean makeReadOnly = CvsConfiguration.getInstance(myProject).MAKE_NEW_FILES_READONLY;
    return CvsUtil.restoreFileFromCachedContent(parent, name, revision, makeReadOnly);
  }
}
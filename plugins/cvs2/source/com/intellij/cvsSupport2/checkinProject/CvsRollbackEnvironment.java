package com.intellij.cvsSupport2.checkinProject;

import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.rollback.DefaultRollbackEnvironment;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class CvsRollbackEnvironment extends DefaultRollbackEnvironment {
  private final Project myProject;

  public CvsRollbackEnvironment(final Project project) {
    myProject = project;
  }

  public List<VcsException> rollbackChanges(List<Change> changes) {
    List<VcsException> exceptions = new ArrayList<VcsException>();

    CvsRollbacker rollbacker = new CvsRollbacker(myProject);
    for (Change change : changes) {
      final FilePath filePath = ChangesUtil.getFilePath(change);
      VirtualFile parent = filePath.getVirtualFileParent();
      String name = filePath.getName();

      try {
        switch (change.getType()) {
          case DELETED:
            rollbacker.rollbackFileDeleting(parent, name);
            break;

          case MODIFICATION:
            rollbacker.rollbackFileModifying(parent, name);
            break;

          case MOVED:
            rollbacker.rollbackFileCreating(parent, name);
            break;

          case NEW:
            rollbacker.rollbackFileCreating(parent, name);
            break;
        }
      }
      catch (IOException e) {
        exceptions.add(new VcsException(e));
      }
    }

    return exceptions;
  }

  public List<VcsException> rollbackMissingFileDeletion(List<FilePath> filePaths) {
    final CvsHandler cvsHandler = CommandCvsHandler.createCheckoutFileHandler(filePaths.toArray(new FilePath[filePaths.size()]),
                                                                              CvsConfiguration.getInstance(myProject));
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(cvsHandler, CvsOperationExecutorCallback.EMPTY);
    return Collections.emptyList();
  }
}
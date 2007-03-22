package com.intellij.cvsSupport2.checkinProject;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.actions.RemoveLocallyFileOrDirectoryAction;
import com.intellij.cvsSupport2.actions.AddFileOrDirectoryAction;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.CvsBundle;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.io.File;
import java.io.IOException;

/**
 * author: lesya
 */
public class CvsCheckinEnvironment implements CheckinEnvironment {

  private final Project myProject;

  public boolean showCheckinDialogInAnyCase() {
    return false;
  }

  public CvsCheckinEnvironment(Project project) {
    myProject = project;
  }

  public RefreshableOnComponent createAdditionalOptionsPanel(final CheckinProjectPanel panel) {
    return null;
    // TODO: shall these options be available elsewhere?
    /*return new CvsProjectAdditionalPanel(panel, myProject);*/
  }

  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    if (filesToCheckin == null) {
      return null;
    }
    if (filesToCheckin.length != 1) {
      return null;
    }
    return CvsUtil.getTemplateFor(filesToCheckin[0]);
  }

  public String prepareCheckinMessage(String text) {
    if (text == null) return null;
    String[] lines = LineTokenizer.tokenize(text.toCharArray(), false);
    StringBuffer buffer = new StringBuffer();
    boolean firstLine = true;
    for (String line : lines) {
      //noinspection HardCodedStringLiteral
      if (!line.startsWith("CVS:")) {
        if (!firstLine) buffer.append(SystemProperties.getLineSeparator());
        buffer.append(line);
        firstLine = false;
      }
    }
    return buffer.toString();
  }

  public String getHelpId() {
    return "cvs.commitProject";
  }

  public String getCheckinOperationName() {
    return CvsBundle.message("operation.name.checkin.project");
  }

  public String getRollbackOperationName() {
    return VcsBundle.message("changes.action.rollback.text");
  }

  public List<VcsException> commit(List<Change> changes, String preparedComment) {
    final Collection<FilePath> filesList = ChangesUtil.getPaths(changes);
    FilePath[] files = filesList.toArray(new FilePath[filesList.size()]);
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.setShowErrors(false);

    final CvsConfiguration cvsConfiguration = CvsConfiguration.getInstance(myProject);

    CvsHandler handler = CommandCvsHandler.createCommitHandler(
          files,
          new File[]{},
          preparedComment,
          CvsBundle.message("operation.name.commit.file", files.length),
          CvsConfiguration.getInstance(myProject).MAKE_NEW_FILES_READONLY, myProject,
          cvsConfiguration.TAG_AFTER_PROJECT_COMMIT,
          cvsConfiguration.TAG_AFTER_PROJECT_COMMIT_NAME);

    executor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    return executor.getResult().getErrorsAndWarnings();
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

  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files) {
    final CvsHandler handler = RemoveLocallyFileOrDirectoryAction.getDefaultHandler(myProject, ChangesUtil.filePathsToFiles(files));
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    return Collections.emptyList();
  }

  public List<VcsException> rollbackMissingFileDeletion(List<FilePath> filePaths) {
    final CvsHandler cvsHandler = CommandCvsHandler.createCheckoutFileHandler(filePaths.toArray(new FilePath[filePaths.size()]),
                                                                              CvsConfiguration.getInstance(myProject));
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(cvsHandler, CvsOperationExecutorCallback.EMPTY);
    return Collections.emptyList();
  }

  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    final CvsHandler handler = AddFileOrDirectoryAction.getDefaultHandler(myProject, files.toArray(new VirtualFile[files.size()]));
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    return Collections.emptyList();
  }

  public List<VcsException> rollbackModifiedWithoutCheckout(final List<VirtualFile> files) {
    throw new UnsupportedOperationException();
  }
}

package com.intellij.cvsSupport2.checkinProject;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.actions.AddFileOrDirectoryAction;
import com.intellij.cvsSupport2.actions.RemoveLocallyFileOrDirectoryAction;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

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

  public List<VcsException> commit(List<Change> changes, String preparedComment) {
    final Collection<FilePath> filesList = ChangesUtil.getPaths(changes);
    FilePath[] files = filesList.toArray(new FilePath[filesList.size()]);
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.setShowErrors(false);

    final List<File> dirsToPrune = new ArrayList<File>();
    for(Change c: changes) {
      if (c.getType() == Change.Type.DELETED) {
        final ContentRevision contentRevision = c.getBeforeRevision();
        assert contentRevision != null;
        final FilePath path = contentRevision.getFile();
        final FilePath parentPath = path.getParentPath();
        if (parentPath != null) {
          dirsToPrune.add(parentPath.getIOFile());
        }
      }
    }

    final CvsConfiguration cvsConfiguration = CvsConfiguration.getInstance(myProject);

    CvsHandler handler = CommandCvsHandler.createCommitHandler(
          files,
          preparedComment,
          CvsBundle.message("operation.name.commit.file", files.length),
          cvsConfiguration.MAKE_NEW_FILES_READONLY, myProject,
          cvsConfiguration.TAG_AFTER_PROJECT_COMMIT,
          cvsConfiguration.TAG_AFTER_PROJECT_COMMIT_NAME,
          dirsToPrune);

    executor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    return executor.getResult().getErrorsAndWarnings();
  }

  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files) {
    final CvsHandler handler = RemoveLocallyFileOrDirectoryAction.getDefaultHandler(myProject, ChangesUtil.filePathsToFiles(files));
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    return Collections.emptyList();
  }

  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    final CvsHandler handler = AddFileOrDirectoryAction.getDefaultHandler(myProject, files.toArray(new VirtualFile[files.size()]));
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    return Collections.emptyList();
  }
}

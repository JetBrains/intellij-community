// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * author: lesya
 */
public class CvsCheckinEnvironment implements CheckinEnvironment {

  private final Project myProject;

  public CvsCheckinEnvironment(Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public RefreshableOnComponent createCommitOptions(@NotNull CheckinProjectPanel commitPanel, @NotNull CommitContext commitContext) {
    return null;
    // TODO: shall these options be available elsewhere?
    /*return new CvsProjectAdditionalPanel(panel, myProject);*/
  }

  @Override
  public String getDefaultMessageFor(FilePath @NotNull [] filesToCheckin) {
    if (filesToCheckin.length != 1) {
      return null;
    }
    return CvsUtil.getTemplateFor(filesToCheckin[0]);
  }

  @Override
  public String getHelpId() {
    return "cvs.commitProject";
  }

  @Override
  public String getCheckinOperationName() {
    return CvsBundle.message("operation.name.checkin.project");
  }

  @Override
  public List<VcsException> commit(@NotNull List<Change> changes,
                                   @NotNull String commitMessage,
                                   @NotNull CommitContext commitContext,
                                   @NotNull Set<String> feedback) {
    final Collection<FilePath> filesList = ChangesUtil.getPaths(changes);
    FilePath[] files = filesList.toArray(new FilePath[0]);
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.setShowErrors(false);

    final List<File> dirsToPrune = new ArrayList<>();
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
      commitMessage,
      CvsBundle.message("operation.name.commit.file", files.length),
      cvsConfiguration.MAKE_NEW_FILES_READONLY, myProject,
      cvsConfiguration.TAG_AFTER_PROJECT_COMMIT,
      cvsConfiguration.TAG_AFTER_PROJECT_COMMIT_NAME,
      dirsToPrune);

    executor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    return executor.getResult().getErrorsAndWarnings();
  }

  @Override
  public List<VcsException> scheduleMissingFileForDeletion(@NotNull List<FilePath> files) {
    for (FilePath file : files) {
      if (file.isDirectory()) {
        VcsBalloonProblemNotifier.showOverChangesView(myProject,
                                                      "Locally deleted directories cannot be removed from CVS. To remove a locally " +
                                                      "deleted directory from CVS, first invoke Rollback and then use " +
                                                      ApplicationNamesInfo.getInstance().getFullProductName() +
                                                      "'s Delete.",
                                                      MessageType.WARNING);
        break;
      }
    }
    final CvsHandler handler = RemoveLocallyFileOrDirectoryAction.getDefaultHandler(myProject, ChangesUtil.filePathsToFiles(files));
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    return Collections.emptyList();
  }

  @Override
  public List<VcsException> scheduleUnversionedFilesForAddition(@NotNull List<VirtualFile> files) {
    final CvsHandler handler = AddFileOrDirectoryAction.getDefaultHandler(myProject, VfsUtil.toVirtualFileArray(files));
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    return Collections.emptyList();
  }

  @Override
  public boolean isRefreshAfterCommitNeeded() {
    return true;
  }
}

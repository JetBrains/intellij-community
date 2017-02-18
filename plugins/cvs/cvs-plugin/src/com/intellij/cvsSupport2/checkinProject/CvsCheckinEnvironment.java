/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FunctionUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;

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

  public RefreshableOnComponent createAdditionalOptionsPanel(final CheckinProjectPanel panel,
                                                             PairConsumer<Object, Object> additionalDataConsumer) {
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

  public String getHelpId() {
    return "cvs.commitProject";
  }

  public String getCheckinOperationName() {
    return CvsBundle.message("operation.name.checkin.project");
  }

  public List<VcsException> commit(List<Change> changes,
                                   String preparedComment,
                                   @NotNull NullableFunction<Object, Object> parametersHolder,
                                   Set<String> feedback) {
    final Collection<FilePath> filesList = ChangesUtil.getPaths(changes);
    FilePath[] files = filesList.toArray(new FilePath[filesList.size()]);
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
          preparedComment,
          CvsBundle.message("operation.name.commit.file", files.length),
          cvsConfiguration.MAKE_NEW_FILES_READONLY, myProject,
          cvsConfiguration.TAG_AFTER_PROJECT_COMMIT,
          cvsConfiguration.TAG_AFTER_PROJECT_COMMIT_NAME,
          dirsToPrune);

    executor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    return executor.getResult().getErrorsAndWarnings();
  }

  public List<VcsException> commit(List<Change> changes, String preparedComment) {
    return commit(changes, preparedComment, FunctionUtil.<Object, Object>nullConstant(), null);
  }

  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files) {
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

  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    final CvsHandler handler = AddFileOrDirectoryAction.getDefaultHandler(myProject, VfsUtil.toVirtualFileArray(files));
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    return Collections.emptyList();
  }

  public boolean keepChangeListAfterCommit(ChangeList changeList) {
    return false;
  }

  @Override
  public boolean isRefreshAfterCommitNeeded() {
    return true;
  }
}

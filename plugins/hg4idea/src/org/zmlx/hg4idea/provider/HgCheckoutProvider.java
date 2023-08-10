// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.ui.VcsCloneComponent;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneStatus;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneTask;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneTaskInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgCloneCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.ui.HgCloneDialog;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.io.File;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.CLONE_ERROR;

public class HgCheckoutProvider implements CheckoutProvider {

  @Override
  public void doCheckout(@NotNull final Project project, @Nullable final Listener listener) {
    FileDocumentManager.getInstance().saveAllDocuments();

    HgCloneDialog dialog = new HgCloneDialog(project);
    if (!dialog.showAndGet()) {
      return;
    }
    dialog.rememberSettings();

    String directoryName = dialog.getDirectoryName();
    String sourceRepositoryURL = dialog.getSourceRepositoryURL();
    String parentDirectory = dialog.getParentDirectory();
    doClone(project, listener, directoryName, sourceRepositoryURL, parentDirectory);
  }

  public static void doClone(@NotNull Project project,
                             @Nullable Listener listener,
                             @NotNull String directoryName,
                             @NotNull String sourceRepositoryURL,
                             @NotNull String parentDirectory) {
    VirtualFile destinationParent = LocalFileSystem.getInstance().findFileByIoFile(new File(parentDirectory));
    if (destinationParent == null) {
      return;
    }
    final String targetDir = destinationParent.getPath() + File.separator + directoryName;
    String projectPath = FileUtilRt.toSystemIndependentName(targetDir);

    CloneTask cloneTask = new CloneTask() {

      @NotNull
      @Override
      public CloneTaskInfo taskInfo() {
        return new CloneTaskInfo(DvcsBundle.message("cloning.repository", sourceRepositoryURL),
                                 DvcsBundle.message("cloning.repository.cancel", sourceRepositoryURL),
                                 DvcsBundle.message("clone.repository"),
                                 DvcsBundle.message("clone.repository.tooltip"),
                                 DvcsBundle.message("clone.repository.failed"),
                                 DvcsBundle.message("clone.repository.canceled"),
                                 DvcsBundle.message("clone.stop.message.title"),
                                 DvcsBundle.message("clone.stop.message.description", sourceRepositoryURL));
      }

      @NotNull
      @Override
      public CloneStatus run(@NotNull ProgressIndicator indicator) {
        HgCloneCommand clone = new HgCloneCommand(project);
        clone.setRepositoryURL(sourceRepositoryURL);
        clone.setDirectory(targetDir);

        HgCommandResult commandResult = clone.executeInCurrentThread();
        if (commandResult == null || HgErrorUtil.hasErrorsInCommandExecution(commandResult)) {
          new HgCommandResultNotifier(project).notifyError(CLONE_ERROR,
                                                           commandResult,
                                                           DvcsBundle.message("clone.repository.failed"),
                                                           HgBundle.message("hg4idea.clone.repo.error.msg", sourceRepositoryURL));

          return CloneStatus.FAILURE;
        }
        else {
          DvcsUtil.addMappingIfSubRoot(project, targetDir, HgVcs.VCS_NAME);
          if (listener != null) {
            listener.directoryCheckedOut(new File(parentDirectory, directoryName), HgVcs.getKey());
            listener.checkoutCompleted();
          }

          return CloneStatus.SUCCESS;
        }
      }
    };

    CloneableProjectsService.getInstance().runCloneTask(projectPath, cloneTask);
  }

  @Override
  public @NotNull String getVcsName() {
    return HgBundle.message("hg4idea.vcs.name.with.mnemonic");
  }

  @NotNull
  @Override
  public VcsCloneComponent buildVcsCloneComponent(@NotNull Project project,
                                                  @NotNull ModalityState modalityState,
                                                  @NotNull VcsCloneDialogComponentStateListener dialogStateListener) {
    return new HgCloneDialogComponent(project, dialogStateListener);
  }
}

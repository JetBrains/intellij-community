// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.ui.VcsCloneComponent;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
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
import java.util.concurrent.atomic.AtomicReference;

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

    final AtomicReference<HgCommandResult> cloneResult = new AtomicReference<>();
    new Task.Backgroundable(project, HgBundle.message("hg4idea.clone.progress", sourceRepositoryURL), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        HgCloneCommand clone = new HgCloneCommand(project);
        clone.setRepositoryURL(sourceRepositoryURL);
        clone.setDirectory(targetDir);
        cloneResult.set(clone.executeInCurrentThread());
      }

      @Override
      public void onSuccess() {
        if (cloneResult.get() == null || HgErrorUtil.hasErrorsInCommandExecution(cloneResult.get())) {
          new HgCommandResultNotifier(project).notifyError(cloneResult.get(), HgBundle.message("hg4idea.clone.error"),
                                                           HgBundle.message("hg4idea.clone.repo.error.msg", sourceRepositoryURL));
        }
        else {
          DvcsUtil.addMappingIfSubRoot(project, targetDir, HgVcs.VCS_NAME);
          if (listener != null) {
            listener.directoryCheckedOut(new File(parentDirectory, directoryName), HgVcs.getKey());
            listener.checkoutCompleted();
          }
        }
      }
    }.queue();
  }

  @Override
  public String getVcsName() {
    return "_Mercurial";
  }

  @NotNull
  @Override
  public VcsCloneComponent buildVcsCloneComponent(@NotNull Project project, @NotNull ModalityState modalityState, @NotNull VcsCloneDialogComponentStateListener dialogStateListener) {
    return new HgCloneDialogComponent(project, dialogStateListener);
  }
}

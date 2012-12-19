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
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgCloneCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.ui.HgCloneDialog;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.io.File;

/**
 * Checkout provider for Mercurial
 */
public class HgCheckoutProvider implements CheckoutProvider {

  public void doCheckout(@NotNull final Project project, @Nullable final Listener listener) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });

    final HgCloneDialog dialog = new HgCloneDialog(project);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    final VirtualFile destinationParent = LocalFileSystem.getInstance().findFileByIoFile(new File(dialog.getParentDirectory()));
    if (destinationParent == null) {
      return;
    }
    final String targetDir = destinationParent.getPath() + File.separator + dialog.getDirectoryName();

    final String sourceRepositoryURL = dialog.getSourceRepositoryURL();
    new Task.Backgroundable(project, HgVcsMessages.message("hg4idea.clone.progress", sourceRepositoryURL), true) {
      @Override public void run(@NotNull ProgressIndicator indicator) {
        // clone
        HgCloneCommand clone = new HgCloneCommand(project);
        clone.setRepositoryURL(sourceRepositoryURL);
        clone.setDirectory(targetDir);

        // handle result
        HgCommandResult myCloneResult = clone.execute();
        if (myCloneResult == null) {
          new HgCommandResultNotifier(project).notifyError(myCloneResult, "Clone failed", "Clone failed due to unknown error");
        } else if (HgErrorUtil.hasErrorsInCommandExecution(myCloneResult)) {
          new HgCommandResultNotifier(project).notifyError(myCloneResult, "Clone failed", "Clone from " +
                                                                                          sourceRepositoryURL +
                                                                                          " failed.");
        } else {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              if (listener != null) {
                listener.directoryCheckedOut(new File(dialog.getParentDirectory(), dialog.getDirectoryName()), HgVcs.getKey());
                listener.checkoutCompleted();
              }
            }
          });
        }
      }
    }.queue();

  }

  /**
   * {@inheritDoc}
   */
  public String getVcsName() {
    return "_Mercurial";
  }
}

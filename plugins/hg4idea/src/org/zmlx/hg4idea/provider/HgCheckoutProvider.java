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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgCloneCommand;
import org.zmlx.hg4idea.command.HgCommandResult;
import org.zmlx.hg4idea.ui.HgCloneDialog;

import java.io.File;

/**
 * Checkout provider for Mercurial
 */
public class HgCheckoutProvider implements CheckoutProvider {

  private HgCommandResult myCloneResult;

  /**
   * {@inheritDoc}
   */
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

    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        HgCloneCommand clone = new HgCloneCommand(project);
        clone.setRepositoryURL(dialog.getSourceRepositoryURL());
        clone.setDirectory(targetDir);
        myCloneResult = clone.execute();

      }
    }, HgVcsMessages.message("hg4idea.clone.progress", dialog.getSourceRepositoryURL()), false, project);

    if (myCloneResult.getExitValue() == 0) {
      if (listener != null) {
        listener.directoryCheckedOut(new File(dialog.getParentDirectory(), dialog.getDirectoryName()));
      }
    }
    if (listener != null) {
      listener.checkoutCompleted();
    }

  }

  /**
   * {@inheritDoc}
   */
  public String getVcsName() {
    return "_Mercurial";
  }
}

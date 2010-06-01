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

import com.intellij.openapi.application.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.*;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.*;
import org.zmlx.hg4idea.ui.*;

import java.io.*;

/**
 * Checkout provider for Mercurial
 */
public class HgCheckoutProvider implements CheckoutProvider {

  /**
   * {@inheritDoc}
   */
  public void doCheckout(@NotNull final Project project, @Nullable final Listener listener) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });

    HgCloneDialog dialog = new HgCloneDialog(project);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    final VirtualFile destinationParent = LocalFileSystem.getInstance().findFileByIoFile(new File(dialog.getParentDirectory()));
    if (destinationParent == null) {
      return;
    }
    String targetDir = destinationParent.getPath() + File.separator + dialog.getDirectoryName();

    final String sourceRepositoryURL = dialog.getSourceRepositoryURL();
    HgCloneCommand clone = new HgCloneCommand(project);
    clone.setRepositoryURL(sourceRepositoryURL);
    clone.setDirectory(targetDir);
    HgCommandResult result = clone.execute();

    HgUtil.markDirectoryDirty(project,destinationParent);

    if (result.getExitValue() == 0) {
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

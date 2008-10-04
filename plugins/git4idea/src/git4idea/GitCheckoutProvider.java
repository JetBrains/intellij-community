/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.actions.BasicAction;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitLineHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Checkout provider fo the Git
 */
public class GitCheckoutProvider implements CheckoutProvider {
  /**
   * {@inheritDoc}
   */
  public void doCheckout(@NotNull final Project project, @Nullable final Listener listener) {
    BasicAction.saveAll();
    GitCheckoutDialog dialog = new GitCheckoutDialog(project);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    final VirtualFile destinationParent = LocalFileSystem.getInstance().findFileByIoFile(new File(dialog.getParentDirectory()));
    if (destinationParent == null) {
      // TODO message
      return;
    }
    final String sourceRepositoryURL = dialog.getSourceRepositoryURL();
    GitLineHandler handler = GitLineHandler
        .clone(project, sourceRepositoryURL, new File(dialog.getParentDirectory()), dialog.getDirectoryName(), dialog.getOriginName());
    int code = GitHandlerUtil.doSynchronously(handler, GitBundle.message("cloning.repository", sourceRepositoryURL), "git clone");
    if (code == 0) {
      if (listener != null) {
        listener.directoryCheckedOut(new File(dialog.getParentDirectory(), dialog.getDirectoryName()));
      }
    }
    if (listener != null) {
      listener.checkoutCompleted();
    }
    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
    mgr.fileDirty(destinationParent);
    destinationParent.refresh(true, true);
  }

  /**
   * {@inheritDoc}
   */
  public String getVcsName() {
    return "_Git";
  }
}

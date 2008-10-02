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

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.actions.BasicAction;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandRunnable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;

/**
 * Checkout provider fo the Git
 */
public class GitCheckoutProvider implements CheckoutProvider {
  /**
   * {@inheritDoc}
   */
  public void doCheckout(@NotNull final Project project, @Nullable final Listener listener) {
    BasicAction.saveAll();
    // TODO: implement remote repository login/password - setup remote repos in Git config,
    // TODO: then just reference repo name here
    GitVcsSettings settings = GitVcsSettings.getInstance(project);
    GitCheckoutDialog dialog = new GitCheckoutDialog(project, settings);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    final VirtualFile destinationParent = LocalFileSystem.getInstance().findFileByIoFile(new File(dialog.getParentDirectory()));
    if (destinationParent == null) {
      // TODO message
      return;
    }
    GitCommandRunnable cmdr = new GitCommandRunnable(project, settings, destinationParent);
    cmdr.setCommand(GitCommand.CLONE_CMD);
    @NonNls ArrayList<String> args = new ArrayList<String>();
    args.add(dialog.getSourceRepositoryURL());
    if (dialog.getOriginName().length() != 0) {
      args.add("-o");
      args.add(dialog.getOriginName());
    }
    args.add(dialog.getDirectoryName());
    final String sourceRepositoryURL = dialog.getSourceRepositoryURL();
    cmdr.setArgs(args.toArray(new String[args.size()]));

    ProgressManager manager = ProgressManager.getInstance();
    //TODO: make this async so the git command output can be seen in the version control window as it happens...
    manager.runProcessWithProgressSynchronously(cmdr, GitBundle.message("cloning.repository", sourceRepositoryURL), false, project);

    @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"}) VcsException ex = cmdr.getException();
    if (ex != null) {
      GitUtil.showOperationError(project, ex, "git clone");
    }
    else {
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

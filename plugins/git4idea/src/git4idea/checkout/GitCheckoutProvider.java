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
package git4idea.checkout;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.Git;
import git4idea.actions.BasicAction;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitUIUtil;
import git4idea.update.GitFetcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Checkout provider for the Git
 */
public class GitCheckoutProvider implements CheckoutProvider {

  private static final Logger LOG = Logger.getInstance(GitCheckoutProvider.class);

  public String getVcsName() {
    return "_Git";
  }

  public void doCheckout(@NotNull final Project project, @Nullable final Listener listener) {
    BasicAction.saveAll();
    GitCloneDialog dialog = new GitCloneDialog(project);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    dialog.rememberSettings();
    final VirtualFile destinationParent = LocalFileSystem.getInstance().findFileByIoFile(new File(dialog.getParentDirectory()));
    if (destinationParent == null) {
      return;
    }
    final String sourceRepositoryURL = dialog.getSourceRepositoryURL();
    final String directoryName = dialog.getDirectoryName();
    final String parentDirectory = dialog.getParentDirectory();
    clone(project, listener, destinationParent, sourceRepositoryURL, directoryName, parentDirectory);
  }

  public static void clone(final Project project,
                           final Listener listener,
                           final VirtualFile destinationParent,
                           final String sourceRepositoryURL,
                           final String directoryName,
                           final String parentDirectory) {

    final AtomicBoolean cloneResult = new AtomicBoolean();
    new Task.Backgroundable(project, GitBundle.message("cloning.repository", sourceRepositoryURL)) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        cloneResult.set(doClone(indicator, project, directoryName, parentDirectory, sourceRepositoryURL));
      }

      @Override
      public void onSuccess() {
        if (!cloneResult.get()) {
          return;
        }

        destinationParent.refresh(true, true, new Runnable() {
          public void run() {
            if (project.isOpen() && (!project.isDisposed()) && (!project.isDefault())) {
              final VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
              mgr.fileDirty(destinationParent);
            }
          }
        });
        listener.directoryCheckedOut(new File(parentDirectory, directoryName));
        listener.checkoutCompleted();
      }
    }.queue();
  }

  private static boolean doClone(ProgressIndicator indicator, Project project, String directoryName, String parentDirectory, String sourceRepositoryURL) {
    final VirtualFile root = mkdir(project, directoryName, parentDirectory);
    if (root == null) { return false; }
    if (!init(project, root)) { return false; }
    if (!addRemote(project, root, sourceRepositoryURL)) { return false; }
    if (!fetch(project, root, indicator)) { return false; }
    return checkout(project, root);
  }

  private static @Nullable VirtualFile mkdir(Project project, String directoryName, String parentDirectory) {
    final File dir = new File(parentDirectory, directoryName);
    if (dir.exists()) {
      GitUIUtil.notifyError(project, "Couldn't clone", "Directory <code>" + dir + "</code> already exists.");
      return null;
    }
    if (!dir.mkdir()) {
      GitUIUtil.notifyError(project, "Couldn't clone", "Can't create directory <code>" + dir + "</code>");
      return null;
    }

    return VcsUtil.getVirtualFileWithRefresh(dir);
  }

  private static boolean init(Project project, VirtualFile root) {
    try {
      Git.init(project, root);
    } catch (VcsException e) {
      LOG.info("init ", e);
      GitUIUtil.notifyError(project, "Couldn't clone", "Couldn't <code>git init</code> in <code>" + root.getPresentableUrl() + "</code>", true, e);
      return false;
    }
    return true;
  }

  private static boolean addRemote(Project project, VirtualFile root, String remoteUrl) {
    final GitSimpleHandler addRemoteHandler = new GitSimpleHandler(project, root, GitCommand.REMOTE);
    addRemoteHandler.setNoSSH(true);
    addRemoteHandler.addParameters("add", "origin", remoteUrl);
    try {
      addRemoteHandler.run();
      return true;
    }
    catch (VcsException e) {
      LOG.info("addRemote ", e);
      GitUIUtil.notifyError(project, "Couldn't clone", "Couldn't add remote <code>" + remoteUrl + "</code>", true, e);
      return false;
    }
  }

  private static boolean fetch(Project project, VirtualFile root, ProgressIndicator indicator) {
    return new GitFetcher(project, indicator).fetch(root);
  }

  private static boolean checkout(Project project, VirtualFile root) {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.CHECKOUT);
    h.setNoSSH(true);
    h.addParameters("-b", "master", "origin/master");
    try {
      h.run();
      return true;
    }
    catch (VcsException e) {
      LOG.info("checkout ", e);
      GitUIUtil.notifyError(project, "Clone not completed",
                            "Couldn't checkout master branch. <br/>All changes were fetched to <code>" + root + "</code>.", true, e);
      return false;
    }
  }

}

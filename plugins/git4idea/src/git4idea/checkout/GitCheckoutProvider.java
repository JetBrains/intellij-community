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

import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.Git;
import git4idea.GitVcs;
import git4idea.actions.BasicAction;
import git4idea.commands.GitCommandResult;
import git4idea.i18n.GitBundle;
import git4idea.jgit.GitHttpAdapter;
import git4idea.update.GitFetchResult;
import git4idea.update.GitFetcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Checkout provider for the Git
 */
public class GitCheckoutProvider implements CheckoutProvider {

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
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final File parent = new File(dialog.getParentDirectory());
    VirtualFile destinationParent = lfs.findFileByIoFile(parent);
    if (destinationParent == null) {
      destinationParent = lfs.refreshAndFindFileByIoFile(parent);
    }
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
        cloneResult.set(doClone(project, directoryName, parentDirectory, sourceRepositoryURL));
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
        listener.directoryCheckedOut(new File(parentDirectory, directoryName), GitVcs.getKey());
        listener.checkoutCompleted();
      }
    }.queue();
  }

  private static boolean doClone(@NotNull Project project, @NotNull String directoryName, @NotNull String parentDirectory, @NotNull String sourceRepositoryURL) {
    if (GitHttpAdapter.isHttpUrlWithoutUserCredentials(sourceRepositoryURL)) {
      GitFetchResult result = GitHttpAdapter.cloneRepository(project, new File(parentDirectory, directoryName), sourceRepositoryURL);
      GitFetcher.displayFetchResult(project, result, "Clone failed", result.getErrors());
      return result.isSuccess();
    }
    else {
      return cloneNatively(project, new File(parentDirectory), sourceRepositoryURL, directoryName);
    }
  }

  private static boolean cloneNatively(Project project, File directory, String url, String cloneDirectoryName) {
    GitCommandResult result = Git.clone(project, directory, url, cloneDirectoryName);
    if (result.success()) {
      return true;
    }
    GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification("Clone failed", result.getErrorOutputAsHtmlString(), NotificationType.ERROR, null)
      .notify(project.isDefault() ? null : project);
    return false;
  }

}

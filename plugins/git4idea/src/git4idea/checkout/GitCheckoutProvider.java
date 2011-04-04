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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import git4idea.actions.BasicAction;
import git4idea.commands.*;
import git4idea.config.GitVersion;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Checkout provider for the Git
 */
public class GitCheckoutProvider implements CheckoutProvider {
  /**
   * The version number since which "-v" options is supported.
   */
  // TODO check if they will actually support the switch in the released 1.6.0.5
  private static final GitVersion VERBOSE_CLONE_SUPPORTED = new GitVersion(1, 6, 0, 5);

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
    checkout(project, listener, destinationParent, sourceRepositoryURL, directoryName, parentDirectory);
  }

  public static void checkout(final Project project,
                              final Listener listener,
                              final VirtualFile destinationParent,
                              final String sourceRepositoryURL,
                              final String directoryName,
                              final String parentDirectory) {
    final GitLineHandler handler = getCloneHandler(project, sourceRepositoryURL, new File(parentDirectory), directoryName);
    GitTask task = new GitTask(project, handler, GitBundle.message("cloning.repository", sourceRepositoryURL));
    task.setProgressAnalyzer(new GitStandardProgressAnalyzer());
    task.executeAsync(new GitTaskResultHandlerAdapter() {
      @Override
      public void onSuccess() {
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

      @Override
      protected void onFailure() {
        GitUIUtil.notifyGitErrors(project, "Couldn't clone", "Couldn't clone from " + sourceRepositoryURL, handler.errors());
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  public String getVcsName() {
    return "_Git";
  }

  /**
   * Prepare clone handler
   *
   * @param project    a project
   * @param url        an url
   * @param directory  a base directory
   * @param name       a name to checkout
   * @param originName origin name (ignored if null or empty string)
   * @return a handler for clone operation
   */
  public static GitLineHandler getCloneHandler(Project project, final String url, final File directory, final String name) {
    GitLineHandler handler = new GitLineHandler(project, directory, GitCommand.CLONE);
    if (VERBOSE_CLONE_SUPPORTED.isOlderOrEqual(GitVcs.getInstance(project).getVersion())) {
      handler.addParameters("-v");
    }
    handler.addParameters(url, name);
    handler.addProgressParameter();
    return handler;
  }
}

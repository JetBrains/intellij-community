package org.jetbrains.plugins.github;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.actions.BasicAction;
import git4idea.checkout.GitCheckoutProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.ui.GithubCloneProjectDialog;
import org.jetbrains.plugins.github.ui.GithubLoginDialog;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author oleg
 */
public class GithubCheckoutProvider implements CheckoutProvider {

  @Override
  public void doCheckout(@NotNull final Project project, @Nullable final Listener listener) {
    BasicAction.saveAll();
    final GithubSettings settings = GithubSettings.getInstance();
    final boolean validCredentials;
    try {
      validCredentials = GithubUtil.accessToGithubWithModalProgress(project, new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          ProgressManager.getInstance().getProgressIndicator().setText("Trying to login to GitHub");
          return GithubUtil.testConnection(settings.getLogin(), settings.getPassword());
        }
      });
    }
    catch (GithubUtil.CancelledException e) {
      return;
    }
    if (!validCredentials){
      final GithubLoginDialog dialog = new GithubLoginDialog(project);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }
    }
    // Otherwise our credentials are valid and they are successfully stored in settings
    final List<RepositoryInfo> availableRepos;
    try {
      availableRepos = GithubUtil.accessToGithubWithModalProgress(project, new Computable<List<RepositoryInfo>>() {
        @Override
        public List<RepositoryInfo> compute() {
          ProgressManager.getInstance().getProgressIndicator().setText("Extracting info about available repositories");
          return GithubUtil.getAvailableRepos(settings.getLogin(), settings.getPassword());
        }
      });
    }
    catch (GithubUtil.CancelledException e) {
      return;
    }
    Collections.sort(availableRepos, new Comparator<RepositoryInfo>() {
      @Override
      public int compare(final RepositoryInfo r1, final RepositoryInfo r2) {
        return r1.getName().compareTo(r2.getName());
      }
    });
    final GithubCloneProjectDialog checkoutDialog = new GithubCloneProjectDialog(project, availableRepos);
    // Configure folder to select project to
    String clonePath = settings.getClonePath();
    final String homePath = System.getProperty("user.home");
    if (StringUtil.isEmpty(clonePath)) {
      clonePath = homePath;
    }
    else {
      final File file = new File(clonePath);
      if (!file.exists() || !file.isDirectory()){
        clonePath = homePath;
      }
    }
    settings.setClonePath(clonePath);
    checkoutDialog.setSelectedPath(clonePath);
    checkoutDialog.show();
    if (!checkoutDialog.isOK()) {
      return;
    }

    // All the preliminary work is already done, go and clone the selected repository!
    final RepositoryInfo selectedRepository = checkoutDialog.getSelectedRepository();
    final String selectedPath = checkoutDialog.getSelectedPath();
    final VirtualFile selectedPathFile = LocalFileSystem.getInstance().findFileByPath(selectedPath);
    final String projectName = checkoutDialog.getProjectName();
    final String repositoryName = selectedRepository.getName();
    final String repositoryOwner = selectedRepository.getOwner();
    final String checkoutUrl = settings.getLogin().equals(repositoryOwner)
                               ? "git@github.com:" + repositoryOwner + "/" + repositoryName + ".git"
                               : "https://github.com/" + repositoryOwner + "/" + repositoryName + ".git";
    GitCheckoutProvider.checkout(project, listener, selectedPathFile, checkoutUrl, projectName, "master", selectedPath);
  }

  @Override
  public String getVcsName() {
    return "_GitHub";
  }
}

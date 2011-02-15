package org.jetbrains.plugins.github;

import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import git4idea.actions.BasicAction;
import git4idea.checkout.GitCheckoutProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.ui.GithubCloneProjectDialog;

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
    final List<RepositoryInfo> availableRepos = GithubUtil.getAvailableRepos(project, false);
    if (availableRepos == null){
      return;
    }
    if (availableRepos.isEmpty()){
      Messages.showErrorDialog(project, "You don't have any repository available on GitHub.\nOnly your own or watched repositories can be cloned.", "Cannot clone");
      return;
    }
    Collections.sort(availableRepos, new Comparator<RepositoryInfo>() {
      @Override
      public int compare(final RepositoryInfo r1, final RepositoryInfo r2) {
        final int comparedOwners = r1.getOwner().compareTo(r2.getOwner());
        return comparedOwners != 0 ? comparedOwners : r1.getName().compareTo(r2.getName());
      }
    });

    // Configure folder to select project to
    String clonePath;
    final String lastProjectLocation = GeneralSettings.getInstance().getLastProjectLocation();
    final String userHome = SystemProperties.getUserHome();
    if (lastProjectLocation != null) {
       clonePath = lastProjectLocation.replace('/', File.separatorChar);
    } else {
      //noinspection HardCodedStringLiteral
      clonePath = userHome.replace('/', File.separatorChar) + File.separator + ApplicationNamesInfo.getInstance().getLowercaseProductName() +
                  "Projects";
    }
    final GithubSettings settings = GithubSettings.getInstance();
    final GithubCloneProjectDialog checkoutDialog = new GithubCloneProjectDialog(project, availableRepos);
    final File file = new File(clonePath);
    if (!file.exists() || !file.isDirectory()){
      clonePath = userHome;
    }
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
    GitCheckoutProvider.checkout(project, listener, selectedPathFile, checkoutUrl, projectName, "origin", selectedPath);
  }

  @Override
  public String getVcsName() {
    return "_GitHub";
  }
}

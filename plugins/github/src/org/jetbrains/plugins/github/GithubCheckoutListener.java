package org.jetbrains.plugins.github;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.checkout.CheckoutListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.github.GitHubRepository;
import com.intellij.tasks.github.GitHubRepositoryType;
import com.intellij.tasks.impl.TaskManagerImpl;
import git4idea.GitRemote;
import git4idea.GitUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author oleg
 * @date 10/26/10
 */
public class GithubCheckoutListener implements CheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(Project project, File directory) {
    return true;
  }

  @Override
  public void processOpenedProject(final Project lastOpenedProject) {
    final GithubSettings settings = GithubSettings.getInstance();
    final String name = getGithubProjectName(lastOpenedProject);
    if (name != null) {
      processProject(lastOpenedProject, settings, name);
    }
  }

  @Nullable
  private String getGithubProjectName(final Project project) {
    final VirtualFile root = project.getBaseDir();
    // Check if git is already initialized and presence of remote branch
    final boolean gitDetected = GitUtil.isUnderGit(root);
    if (gitDetected) {
      try {
        final List<GitRemote> gitRemotes = GitRemote.list(project, root);
        for (GitRemote gitRemote : gitRemotes) {
          String url = gitRemote.fetchUrl();
          if (url.contains("github.com")) {
            final int i = url.lastIndexOf("/");
            if (i == -1){
              return project.getName();
            }
            url = url.substring(i + 1);
            if (url.endsWith(".git")){
              url = url.substring(0, url.length() - 4);
            }
            return url;
          }
        }
      }
      catch (VcsException e2) {
        // ingore
      }
    }
    return null;
  }

  private void processProject(final Project openedProject, final GithubSettings settings, final String name) {
    // try to enable git tasks integration
    final Runnable taskInitializationRunnable = new Runnable() {
      public void run() {
        try {
          enableGithubTrackerIntergation(openedProject, settings.getLogin(), settings.getPassword(), name);
        }
        catch (Exception e) {
          // Ignore it
        }
      }
    };
    if (openedProject.isInitialized()) {
      taskInitializationRunnable.run();
    } else {
      StartupManager.getInstance(openedProject).runWhenProjectIsInitialized(taskInitializationRunnable);
    }
  }

  private void enableGithubTrackerIntergation(final Project project, final String login, final String password, final String name) {
    // Look for github repository type
    final TaskManagerImpl manager = (TaskManagerImpl)TaskManager.getManager(project);
    final TaskRepository[] allRepositories = manager.getAllRepositories();
    for (TaskRepository repository : allRepositories) {
      if (repository instanceof GitHubRepository) {
        return;
      }
    }
    // Create new one if not found exists
    final GitHubRepository repository = new GitHubRepository(new GitHubRepositoryType());
    repository.setUsername(login);
    repository.setPassword(password);
    repository.setRepoName(name);
    repository.setRepoAuthor(login);
    final ArrayList<TaskRepository> repositories = new ArrayList<TaskRepository>(Arrays.asList(allRepositories));
    repositories.add(repository);
    manager.setRepositories(repositories);
  }


}

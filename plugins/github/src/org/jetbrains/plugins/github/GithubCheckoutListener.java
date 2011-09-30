package org.jetbrains.plugins.github;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.checkout.CheckoutListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.github.GitHubRepository;
import com.intellij.tasks.github.GitHubRepositoryType;
import com.intellij.tasks.impl.TaskManagerImpl;
import git4idea.GitDeprecatedRemote;
import git4idea.GitUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author oleg
 * @date 10/26/10
 */
public class GithubCheckoutListener implements CheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(Project project, File directory) {
    return false;
  }

  @Override
  public void processOpenedProject(final Project lastOpenedProject) {
    final GithubSettings settings = GithubSettings.getInstance();
    final Pair<String, String> info = getGithubProjectInfo(lastOpenedProject);
    if (info != null) {
      processProject(lastOpenedProject, settings, info.first, info.second);
    }
  }

  @Nullable
  private Pair<String, String> getGithubProjectInfo(final Project project) {
    final VirtualFile root = project.getBaseDir();
    // Check if git is already initialized and presence of remote branch
    final boolean gitDetected = GitUtil.isUnderGit(root);
    if (gitDetected) {
      final GitDeprecatedRemote gitRemote = GithubUtil.findGitHubRemoteBranch(project, root);
      if (gitRemote == null) {
        return null;
      }
      String url = gitRemote.fetchUrl();
      if (url.contains("github.com")) {
        int i = url.lastIndexOf("/");
        if (i == -1){
          return null;
        }
        String name = url.substring(i + 1);
        if (name.endsWith(".git")){
          name = name.substring(0, name.length() - 4);
        }
        url = url.substring(0, i);
        // We don't want https://
        if (url.startsWith("https://")){
          url = url.substring(8);
        }
        i = url.lastIndexOf(':');
        if (i == -1) {
          i = url.lastIndexOf('/');
        }
        if (i == -1){
          return null;
        }
        final String author = url.substring(i + 1);
        return Pair.create(author, name);
      }
    }
    return null;
  }

  private void processProject(final Project openedProject, final GithubSettings settings, final String author, final String name) {
    // try to enable git tasks integration
    final Runnable taskInitializationRunnable = new Runnable() {
      public void run() {
        try {
          enableGithubTrackerIntergation(openedProject, settings.getLogin(), settings.getPassword(), author, name);
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

  private void enableGithubTrackerIntergation(final Project project, final String login, final String password, final String author, final String name) {
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
    repository.setRepoAuthor(author);
    repository.setRepoName(name);
    final ArrayList<TaskRepository> repositories = new ArrayList<TaskRepository>(Arrays.asList(allRepositories));
    repositories.add(repository);
    manager.setRepositories(repositories);
  }

}
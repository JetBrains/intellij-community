package org.jetbrains.plugins.github.extensions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vcs.checkout.CheckoutListener;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.impl.TaskManagerImpl;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.tasks.GithubRepository;
import org.jetbrains.plugins.github.tasks.GithubRepositoryType;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

// TODO: remove ?

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
    //final GithubFullPath info = getGithubProjectInfo(lastOpenedProject);
    //if (info != null) {
    //  processProject(lastOpenedProject, info.getUser(), info.getRepository());
    //}
  }

  @Nullable
  private static GithubFullPath getGithubProjectInfo(final Project project) {
    final GitRepository gitRepository = GithubUtil.getGitRepository(project, null);
    if (gitRepository == null) {
      return null;
    }

    // Check that given repository is properly configured git repository
    String url = GithubUtil.findGithubRemoteUrl(gitRepository);
    if (url == null) {
      return null;
    }
    return GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url);
  }

  private static void processProject(final Project openedProject, final String author, final String name) {
    // try to enable git tasks integration
    final Runnable taskInitializationRunnable = new Runnable() {
      public void run() {
        try {
          enableGithubTrackerIntegration(openedProject, author, name);
        }
        catch (Exception e) {
          // Ignore it
        }
      }
    };
    if (openedProject.isInitialized()) {
      taskInitializationRunnable.run();
    }
    else {
      StartupManager.getInstance(openedProject).runWhenProjectIsInitialized(taskInitializationRunnable);
    }
  }

  private static void enableGithubTrackerIntegration(final Project project, final String author, final String name) {
    // Look for github repository type
    final TaskManagerImpl manager = (TaskManagerImpl)TaskManager.getManager(project);
    final TaskRepository[] allRepositories = manager.getAllRepositories();
    for (TaskRepository repository : allRepositories) {
      if (repository instanceof GithubRepository) {
        return;
      }
    }
    // Create new one if not found exists
    final GithubRepository repository = new GithubRepository(new GithubRepositoryType());
    repository.setToken("");
    repository.setRepoAuthor(author);
    repository.setRepoName(name);
    final ArrayList<TaskRepository> repositories = new ArrayList<TaskRepository>(Arrays.asList(allRepositories));
    repositories.add(repository);
    manager.setRepositories(repositories);
  }

}
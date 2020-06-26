package com.intellij.jps.cache;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.intellij.execution.process.ProcessIOExecutorService.INSTANCE;

public final class JpsCachesPluginUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsCachesPluginUtil");
  public static final String PLUGIN_NAME = "jps-cache-loader";
  public static final ExecutorService EXECUTOR_SERVICE = AppExecutorUtil.createBoundedApplicationPoolExecutor("JpsCacheLoader Pool",
                                                                                                         INSTANCE, getThreadPoolSize());
  private JpsCachesPluginUtil() {}

  private static int getThreadPoolSize() {
    int threadsCount = Runtime.getRuntime().availableProcessors() - 1;
    LOG.info("Executor service will be configured with " + threadsCount + " threads");
    return threadsCount;
  }

  public static @Nullable String getCurrentGitBranch(@NotNull Project project) {
    List<GitRepository> repositories = GitRepositoryManager.getInstance(project).getRepositories();
    if (repositories.size() != 1) {
      LOG.warn("Unable to detect current branch for the project with the multiple git repositories");
      return null;
    }
    return repositories.get(0).getCurrentBranchName();
  }
}

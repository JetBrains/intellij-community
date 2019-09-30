package com.intellij.jps.cache.loader;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.jps.cache.JpsCachesUtils;
import com.intellij.jps.cache.client.ArtifactoryJpsServerClient;
import com.intellij.jps.cache.client.JpsServerClient;
import com.intellij.jps.cache.git.GitRepositoryUtil;
import com.intellij.jps.cache.hashing.PersistentCachingModuleHashingService;
import com.intellij.jps.cache.loader.JpsOutputLoader.LoaderStatus;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class JpsOutputLoaderManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.loader.JpsOutputLoaderManager");
  private static final String LATEST_COMMIT_ID = "JpsOutputLoaderManager.latestCommitId";
  private PersistentCachingModuleHashingService myModuleHashingService;
  private final ExecutorService ourThreadPool;
  private List<JpsOutputLoader> myJpsOutputLoadersLoaders;
  private final JpsServerClient myServerClient;
  private final Project myProject;

  @NotNull
  public static JpsOutputLoaderManager getInstance(@NotNull Project project) {
    JpsOutputLoaderManager component = project.getComponent(JpsOutputLoaderManager.class);
    assert component != null;
    return component;
  }

  public JpsOutputLoaderManager(@NotNull Project project) {
    myProject = project;
    myServerClient = ArtifactoryJpsServerClient.INSTANCE;
    ourThreadPool = AppExecutorUtil.createBoundedApplicationPoolExecutor("JpsCacheLoader Pool", ProcessIOExecutorService.INSTANCE,
                                                                         getThreadPoolSize());
  }

  public void initialize(@NotNull Project project) {
    try {
      myModuleHashingService = new PersistentCachingModuleHashingService(new File(JpsCachesUtils.getPluginStorageDir(project)), project);
    }
    catch (Exception e) {
      LOG.warn("Couldn't instantiate module hashing service", e);
    }
  }

  public void load() {
    ourThreadPool.execute(() -> load(myServerClient.getAllCacheKeys()));
  }

  public void load(@NotNull String currentCommitId) {
    ourThreadPool.execute(() -> {
      String previousCommitId = PropertiesComponent.getInstance().getValue(LATEST_COMMIT_ID);
      if (previousCommitId != null && currentCommitId.equals(previousCommitId)) {
        LOG.debug("Caches already for commit: " + currentCommitId);
        return;
      }
      Set<String> allCacheKeys = myServerClient.getAllCacheKeys();
      if (allCacheKeys.contains(currentCommitId)) {
        startLoadingForCommit(currentCommitId);
      }
      else {
        load(allCacheKeys);
      }
    });
  }

  public void close() {
    myModuleHashingService.close();
  }

  private void load(Set<String> allCacheKeys) {
    String previousCommitId = PropertiesComponent.getInstance().getValue(LATEST_COMMIT_ID);
    Iterator<String> commitsIterator = GitRepositoryUtil.getCommitsIterator(myProject);
    String commitId= "";
    while (commitsIterator.hasNext() && !allCacheKeys.contains(commitId)) {
      commitId = commitsIterator.next();
    }

    if (!allCacheKeys.contains(commitId)) {
      LOG.warn("Not found any caches for the latest commits in the brunch");
      return;
    }
    if (previousCommitId != null && commitId.equals(previousCommitId)) {
      LOG.debug("The system contains up to date caches");
      return;
    }
    startLoadingForCommit(commitId);
  }

  private void startLoadingForCommit(String commitId) {
    List<CompletableFuture<LoaderStatus>> completableFutures =
      ContainerUtil.map(getLoaders(myProject), loader -> CompletableFuture.supplyAsync(() -> loader.load(commitId), ourThreadPool));

    CompletableFuture<LoaderStatus> initialFuture = completableFutures.get(0);
    if (completableFutures.size() > 1) {
      for (int i = 1; i < completableFutures.size(); i++) {
        initialFuture = initialFuture.thenCombine(completableFutures.get(i), JpsOutputLoaderManager::combine);
      }
    }

    // Computation with loaders results. If at least one of them failed rollback all job
    initialFuture.thenAccept(loaderStatus -> {
      LOG.debug("Loaders finished with " + loaderStatus + " status");
      CompletableFuture.allOf(getLoaders(myProject).stream().map(loader -> {
        if (loaderStatus == LoaderStatus.FAILED) {
          return CompletableFuture.runAsync(() -> loader.rollback(), ourThreadPool);
        }
        return CompletableFuture.runAsync(() -> loader.apply(), ourThreadPool);
      }).toArray(CompletableFuture[]::new))
        .thenRun(() -> {
          if (loaderStatus == LoaderStatus.COMPLETE) {
            PropertiesComponent.getInstance().setValue(LATEST_COMMIT_ID, commitId);
            LOG.debug("Loaders finished");
          }
        });
    });
  }

  private List<JpsOutputLoader> getLoaders(@NotNull Project project) {
    if (myJpsOutputLoadersLoaders != null) return myJpsOutputLoadersLoaders;
    myJpsOutputLoadersLoaders = Arrays.asList(new JpsCacheLoader(myServerClient, project),
                                              new JpsCompilationOutputLoader(myServerClient, project, myModuleHashingService));
    return myJpsOutputLoadersLoaders;
  }

  private static LoaderStatus combine(LoaderStatus firstStatus, LoaderStatus secondStatus) {
    if (firstStatus == LoaderStatus.FAILED || secondStatus == LoaderStatus.FAILED) return LoaderStatus.FAILED;
    return LoaderStatus.COMPLETE;
  }

  private static int getThreadPoolSize() {
    return (Runtime.getRuntime().availableProcessors() / 2) > 3 ? 3 : 1;
  }
}

package com.intellij.jps.cache.loader;

import com.intellij.CommonBundle;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.jps.cache.JpsCachesUtils;
import com.intellij.jps.cache.client.ArtifactoryJpsServerClient;
import com.intellij.jps.cache.client.JpsServerClient;
import com.intellij.jps.cache.git.GitRepositoryUtil;
import com.intellij.jps.cache.hashing.PersistentCachingModuleHashingService;
import com.intellij.jps.cache.loader.JpsOutputLoader.LoaderStatus;
import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class JpsOutputLoaderManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.loader.JpsOutputLoaderManager");
  private static final String LATEST_COMMIT_ID = "JpsOutputLoaderManager.latestCommitId";
  private static final String GROUP_DISPLAY_ID = "Jps Cache Loader";
  private static final double TOTAL_SEGMENT_SIZE = 0.9;
  private PersistentCachingModuleHashingService myModuleHashingService;
  private final AtomicBoolean hasRunningTask;
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
    hasRunningTask = new AtomicBoolean();
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
    if (!canRunNewLoading()) return;
    ourThreadPool.execute(() -> load(myServerClient.getAllCacheKeys()));
  }

  public void load(@NotNull String currentCommitId) {
    if (!canRunNewLoading()) return;
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
    ProgressManager.getInstance().runProcess(() -> {
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      indicator.setText("Calculating nearest commit with portable cache");

      String previousCommitId = PropertiesComponent.getInstance().getValue(LATEST_COMMIT_ID);
      Iterator<String> commitsIterator = GitRepositoryUtil.getCommitsIterator(myProject);
      String commitId = "";
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
    }, createProcessIndicator(myProject));
  }

  private void startLoadingForCommit(String commitId) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.setText("Fetching cache for commit: " + commitId);
    indicator.setFraction(0.05);
    List<JpsOutputLoader> loaders = getLoaders(myProject);
    List<CompletableFuture<LoaderStatus>> completableFutures =
      ContainerUtil.map(loaders, loader -> {
        return CompletableFuture.supplyAsync(() -> {
          SegmentedProgressIndicatorManager indicatorManager = new SegmentedProgressIndicatorManager(indicator, TOTAL_SEGMENT_SIZE / loaders.size());
          return loader.load(commitId, indicatorManager);
        }, ourThreadPool);
      });

    CompletableFuture<LoaderStatus> initialFuture = completableFutures.get(0);
    if (completableFutures.size() > 1) {
      for (int i = 1; i < completableFutures.size(); i++) {
        initialFuture = initialFuture.thenCombine(completableFutures.get(i), JpsOutputLoaderManager::combine);
      }
    }

    try {
      // Computation with loaders results. If at least one of them failed rollback all job
      initialFuture.thenAccept(loaderStatus -> {
        LOG.debug("Loading finished with " + loaderStatus + " status");
        CompletableFuture.allOf(getLoaders(myProject).stream().map(loader -> {
          indicator.setFraction(0.97);
          if (loaderStatus == LoaderStatus.FAILED) {
            indicator.setText("Fetching cache failed, rolling back");
            return CompletableFuture.runAsync(() -> loader.rollback(), ourThreadPool);
          }
          indicator.setText("Fetching cache complete successfully, applying changes ");
          return CompletableFuture.runAsync(() -> loader.apply(), ourThreadPool);
        }).toArray(CompletableFuture[]::new))
          .thenRun(() -> {
            if (loaderStatus == LoaderStatus.COMPLETE) {
              PropertiesComponent.getInstance().setValue(LATEST_COMMIT_ID, commitId);
              ApplicationManager.getApplication().invokeLater(() -> {
                Notification notification = new Notification(GROUP_DISPLAY_ID, GROUP_DISPLAY_ID, "Jps cache loaded successfully for commit " + commitId,
                                                             NotificationType.INFORMATION);
                Notifications.Bus.notify(notification);
              });
              LOG.debug("Loading finished");
            } else {
              ApplicationManager.getApplication().invokeLater(() -> {
                Notification notification = new Notification(GROUP_DISPLAY_ID, GROUP_DISPLAY_ID, "Couldn't load jps cache for commit " + commitId,
                                                             NotificationType.WARNING);
                Notifications.Bus.notify(notification);
              });
            }
          });
      }).handle((result, ex) -> {
        if (ex != null) {
          Throwable cause = ex.getCause();
          if (cause instanceof ProcessCanceledException) {
            LOG.info("Jps caches download canceled");
          }
          else {
            LOG.warn("Couldn't fetch jps compilation caches", ex);
          }
          loaders.forEach(loader -> loader.rollback());
          indicator.setText("Rolling back downloaded caches");
        }
        return result;
      }).get();
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.warn("Couldn't fetch jps compilation caches", e);
    }
    hasRunningTask.set(false);
  }

  private synchronized boolean canRunNewLoading() {
    if (hasRunningTask.get()) {
      LOG.debug("Jps cache loading already in progress, can't start the new one");
      return false;
    }
    hasRunningTask.set(true);
    return true;
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

  private static ProgressIndicator createProcessIndicator(@NotNull Project project) {
    BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(project, "Updating Compilation Caches",
                                                                                         PerformInBackgroundOption.ALWAYS_BACKGROUND,
                                                                                         CommonBundle.getCancelButtonText(),
                                                                                         CommonBundle.getCancelButtonText(), true);
    processIndicator.setIndeterminate(false);
    return processIndicator;
  }
}
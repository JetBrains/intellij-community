package com.intellij.jps.cache.loader;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.jps.cache.JpsCachesUtils;
import com.intellij.jps.cache.client.ArtifactoryJpsServerClient;
import com.intellij.jps.cache.client.JpsServerClient;
import com.intellij.jps.cache.git.GitRepositoryUtil;
import com.intellij.jps.cache.hashing.PersistentCachingModuleHashingService;
import com.intellij.jps.cache.loader.JpsOutputLoader.LoaderStatus;
import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.jps.cache.ui.JpsLoaderNotifications.NONE_NOTIFICATION_GROUP;
import static com.intellij.jps.cache.ui.JpsLoaderNotifications.STICKY_NOTIFICATION_GROUP;

public class JpsOutputLoaderManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.loader.JpsOutputLoaderManager");
  private static final String LATEST_COMMIT_ID = "JpsOutputLoaderManager.latestCommitId";
  private static final String PROGRESS_TITLE = "Updating Compilation Caches";
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

  public boolean isInitialized() {
    return myModuleHashingService != null;
  }

  public void load(boolean isForceUpdate) {
    if (!isInitialized()) {
      String message = "The plugin context is not yet initialized. Please try again later";
      Notification notification = NONE_NOTIFICATION_GROUP.createNotification("Compile Output Loader", message,
                                                                             NotificationType.INFORMATION, null);
      Notifications.Bus.notify(notification);
      LOG.info(message);
      return;
    }

    Task.Backgroundable task = new Task.Backgroundable(myProject, PROGRESS_TITLE) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Pair<String, Integer> commitInfo = getNearestCommit(isForceUpdate);
        if (commitInfo == null) return;
        startLoadingForCommit(commitInfo.first);
      }
    };
    BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(task);
    processIndicator.setIndeterminate(false);
    if (!canRunNewLoading()) return;
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
  }

  public void notifyAboutNearestCache() {
    if (!isInitialized()) {
      LOG.warn("The plugin context is not yet initialized");
      return;
    }

    ourThreadPool.execute(() -> {
      Pair<String, Integer> commitInfo = getNearestCommit(false);
      if (commitInfo == null) return;

      String commitToLoad = commitInfo.first;
      String notificationContent = commitInfo.second == 0 ? "Compile server contains caches for the current commit. Do you want to update your data?"
                                                      : "Compile server contains caches for the " + commitInfo.second + "th commit behind of yours. Do you want to update your data?";
      ApplicationManager.getApplication().invokeLater(() -> {
        Notification notification = STICKY_NOTIFICATION_GROUP.createNotification("Compile Output Loader", notificationContent,
                                                                                 NotificationType.INFORMATION, null);
        notification.addAction(NotificationAction.createSimple("Update Compile Caches", () -> {
          notification.expire();
          Task.Backgroundable task = new Task.Backgroundable(myProject, PROGRESS_TITLE) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              startLoadingForCommit(commitToLoad);
            }
          };
          BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(task);
          processIndicator.setIndeterminate(false);
          if (!canRunNewLoading()) return;
          ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
        }));
        Notifications.Bus.notify(notification);
      });
    });
  }

  @Nullable
  private Pair<String, Integer> getNearestCommit(boolean isForceUpdate) {
    Set<String> allCacheKeys = myServerClient.getAllCacheKeys();

    String previousCommitId = PropertiesComponent.getInstance().getValue(LATEST_COMMIT_ID);
    Iterator<String> commitsIterator = GitRepositoryUtil.getCommitsIterator(myProject);
    String commitId = "";
    int commitsBehind = 0;
    while (commitsIterator.hasNext() && !allCacheKeys.contains(commitId)) {
      commitId = commitsIterator.next();
      commitsBehind++;
    }

    if (!allCacheKeys.contains(commitId)) {
      LOG.warn("Not found any caches for the latest commits in the brunch");
      return null;
    }
    if (previousCommitId != null && commitId.equals(previousCommitId) && !isForceUpdate) {
      LOG.debug("The system contains up to date caches");
      return null;
    }
    return Pair.create(commitId, commitsBehind);
  }

  public void close() {
    myModuleHashingService.close();
  }

  private void startLoadingForCommit(String commitId) {
    long startTime = System.currentTimeMillis();
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
              long endTime = (System.currentTimeMillis() - startTime) / 1000;
              ApplicationManager.getApplication().invokeLater(() -> {
                String message = "Update compilation caches completed successfully in " + endTime + " s";
                Notification notification = NONE_NOTIFICATION_GROUP.createNotification("Compile Output Loader", message,
                                                                                       NotificationType.INFORMATION, null);
                Notifications.Bus.notify(notification);
              });
              LOG.debug("Loading finished");
            } else onFail();
          });
      }).handle((result, ex) -> {
        if (ex != null) {
          Throwable cause = ex.getCause();
          if (cause instanceof ProcessCanceledException) {
            LOG.info("Jps caches download canceled");
          }
          else {
            LOG.warn("Couldn't fetch jps compilation caches", ex);
            onFail();
          }
          loaders.forEach(loader -> loader.rollback());
          indicator.setText("Rolling back downloaded caches");
        }
        return result;
      }).get();
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.warn("Couldn't fetch jps compilation caches", e);
      onFail();
    }
    hasRunningTask.set(false);
  }

  private synchronized boolean canRunNewLoading() {
    if (hasRunningTask.get()) {
      LOG.warn("Jps cache loading already in progress, can't start the new one");
      return false;
    }
    hasRunningTask.set(true);
    return true;
  }

  private List<JpsOutputLoader> getLoaders(@NotNull Project project) {
    if (myJpsOutputLoadersLoaders != null) return myJpsOutputLoadersLoaders;
    myJpsOutputLoadersLoaders = Arrays.asList(new JpsCacheLoader(myServerClient, project),
                                              new JpsProductionOutputLoader(myServerClient, project, myModuleHashingService),
                                              new JpsTestOutputLoader(myServerClient, project, myModuleHashingService));
    return myJpsOutputLoadersLoaders;
  }

  private static LoaderStatus combine(LoaderStatus firstStatus, LoaderStatus secondStatus) {
    if (firstStatus == LoaderStatus.FAILED || secondStatus == LoaderStatus.FAILED) return LoaderStatus.FAILED;
    return LoaderStatus.COMPLETE;
  }

  private static int getThreadPoolSize() {
    return (Runtime.getRuntime().availableProcessors() / 2) > 3 ? 3 : 1;
  }

  private static void onFail() {
    ApplicationManager.getApplication().invokeLater(() -> {
      Notification notification = NONE_NOTIFICATION_GROUP.createNotification("Compile Output Loader", "Update compilation caches failed",
                                                                             NotificationType.WARNING, null);
      Notifications.Bus.notify(notification);
    });
  }
}
package com.intellij.jps.cache.loader;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.jps.cache.client.ArtifactoryJpsServerClient;
import com.intellij.jps.cache.client.JpsServerClient;
import com.intellij.jps.cache.git.GitRepositoryUtil;
import com.intellij.jps.cache.loader.JpsOutputLoader.LoaderStatus;
import com.intellij.jps.cache.model.BuildTargetState;
import com.intellij.jps.cache.model.JpsLoaderContext;
import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
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
  private final AtomicBoolean hasRunningTask;
  private final ExecutorService ourThreadPool;
  private List<JpsOutputLoader> myJpsOutputLoadersLoaders;
  private final JpsMetadataLoader myMetadataLoader;
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
    myMetadataLoader = new JpsMetadataLoader(project, myServerClient);
    ourThreadPool = AppExecutorUtil.createBoundedApplicationPoolExecutor("JpsCacheLoader Pool", ProcessIOExecutorService.INSTANCE,
                                                                         getThreadPoolSize());
  }

  public void load(boolean isForceUpdate) {
    Task.Backgroundable task = new Task.Backgroundable(myProject, PROGRESS_TITLE) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Pair<String, Integer> commitInfo = getNearestCommit(isForceUpdate);
        if (commitInfo == null) return;
        startLoadingForCommit(commitInfo.first);
        hasRunningTask.set(false);
      }
    };

    BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(task);
    processIndicator.setIndeterminate(false);
    if (!canRunNewLoading()) return;
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
  }

  public void notifyAboutNearestCache() {
    ourThreadPool.execute(() -> {
      Pair<String, Integer> commitInfo = getNearestCommit(false);
      if (commitInfo == null) return;

      String notificationContent = commitInfo.second == 0
                                   ? "Compile server contains caches for the current commit. Do you want to update your data?"
                                   : "Compile server contains caches for the " + commitInfo.second + "th commit behind of yours. Do you want to update your data?";

      ApplicationManager.getApplication().invokeLater(() -> {
        Notification notification = STICKY_NOTIFICATION_GROUP.createNotification("Compile Output Loader", notificationContent,
                                                                                 NotificationType.INFORMATION, null);
        notification.addAction(NotificationAction.createSimple("Update Compile Caches", () -> {
          notification.expire();
          load(false);
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
      LOG.info("The system contains up to date caches");
      return null;
    }
    return Pair.create(commitId, commitsBehind);
  }

  private void startLoadingForCommit(@NotNull String commitId) {
    long startTime = System.currentTimeMillis();
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.setText("Fetching cache for commit: " + commitId);

    // Loading metadata for commit
    Map<String, Map<String, BuildTargetState>> commitSourcesState = myMetadataLoader.loadMetadataForCommit(commitId);
    if (commitSourcesState == null) {
      LOG.warn("Couldn't load metadata for commit: " + commitId);
      return;
    }
    indicator.setFraction(0.01);
    Map<String, Map<String, BuildTargetState>> currentSourcesState = myMetadataLoader.loadCurrentProjectMetadata();

    List<JpsOutputLoader> loaders = getLoaders(myProject);
    List<CompletableFuture<LoaderStatus>> completableFutures =
      ContainerUtil.map(loaders, loader -> {
        return CompletableFuture.supplyAsync(() -> {
          SegmentedProgressIndicatorManager indicatorManager = new SegmentedProgressIndicatorManager(indicator, TOTAL_SEGMENT_SIZE / loaders.size());
          return loader.load(JpsLoaderContext.createNewContext(commitId, indicatorManager, commitSourcesState, currentSourcesState));
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
        LOG.info("Loading finished with " + loaderStatus + " status");
        CompletableFuture<Void> rollbackApplyFuture = CompletableFuture.allOf(getLoaders(myProject).stream().map(loader -> {
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
              LOG.info("Loading finished");
            } else onFail();
          });

        try {
          rollbackApplyFuture.get();
        }
        catch (InterruptedException | ExecutionException e) {
          LOG.warn("Unexpected exception rollback all progress", e);
          onFail();
          loaders.forEach(loader -> loader.rollback());
          indicator.setText("Rolling back downloaded caches");
        }
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
                                              new JpsCompilationOutputLoader(myServerClient, project));
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
package com.intellij.jps.cache.loader;

import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.server.BuildManager;
import com.intellij.ide.util.PropertiesComponent;
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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.execution.process.ProcessIOExecutorService.INSTANCE;
import static com.intellij.jps.cache.ui.JpsLoaderNotifications.NONE_NOTIFICATION_GROUP;
import static com.intellij.jps.cache.ui.JpsLoaderNotifications.STICKY_NOTIFICATION_GROUP;

public class JpsOutputLoaderManager {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.loader.JpsOutputLoaderManager");
  private static final String LATEST_COMMIT_ID = "JpsOutputLoaderManager.latestCommitId";
  private static final String PROGRESS_TITLE = "Updating Compiler Caches";
  private static final double SEGMENT_SIZE = 0.33;
  private final AtomicBoolean hasRunningTask;
  private final CompilerWorkspaceConfiguration myWorkspaceConfiguration;
  private List<JpsOutputLoader<?>> myJpsOutputLoadersLoaders;
  private final JpsMetadataLoader myMetadataLoader;
  private final JpsServerClient myServerClient;
  private final Project myProject;

  @NotNull
  public static JpsOutputLoaderManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, JpsOutputLoaderManager.class);
  }

  public JpsOutputLoaderManager(@NotNull Project project) {
    myProject = project;
    hasRunningTask = new AtomicBoolean();
    myServerClient = JpsServerClient.getServerClient();
    myMetadataLoader = new JpsMetadataLoader(project, myServerClient);
    myWorkspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
    // Configure build manager
    BuildManager buildManager = BuildManager.getInstance();
    if (!buildManager.isGeneratePortableCachesEnabled()) buildManager.setGeneratePortableCachesEnabled(true);
  }

  public void load(boolean isForceUpdate) {
    Task.Backgroundable task = new Task.Backgroundable(myProject, PROGRESS_TITLE) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Pair<String, Integer> commitInfo = getNearestCommit(isForceUpdate);
        if (commitInfo != null) {
          // Drop JPS metadata to force plugin for downloading all compilation outputs
          if (isForceUpdate) myMetadataLoader.dropCurrentProjectMetadata();
          startLoadingForCommit(commitInfo.first);
        }
        hasRunningTask.set(false);
      }
    };

    if (!canRunNewLoading()) return;
    BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(task);
    processIndicator.setIndeterminate(false);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
  }

  public void notifyAboutNearestCache() {
    INSTANCE.execute(() -> {
      Pair<String, Integer> commitInfo = getNearestCommit(false);
      if (commitInfo == null) return;

      String notificationContent = commitInfo.second == 1
                                   ? "Caches are for the current commit."
                                   : "Caches are for the commit " + (commitInfo.second - 1) + " commits prior to yours.";

      ApplicationManager.getApplication().invokeLater(() -> {
        Notification notification = STICKY_NOTIFICATION_GROUP.createNotification("Compiler caches available", notificationContent,
                                                                                 NotificationType.INFORMATION, null);
        notification.addAction(NotificationAction.createSimple("Update Caches", () -> {
          notification.expire();
          load(false);
        }));
        Notifications.Bus.notify(notification, myProject);
      });
    });
  }

  @Nullable
  private Pair<String, Integer> getNearestCommit(boolean isForceUpdate) {
    Set<String> allCacheKeys = myServerClient.getAllCacheKeys();

    String previousCommitId = PropertiesComponent.getInstance().getValue(LATEST_COMMIT_ID);
    List<Iterator<String>> repositoryList = GitRepositoryUtil.getCommitsIterator(myProject);
    String commitId = "";
    int commitsBehind = 0;
    for (Iterator<String> commitsIterator : repositoryList) {
      if (allCacheKeys.contains(commitId)) continue;
      commitsBehind = 0;
      while (commitsIterator.hasNext() && !allCacheKeys.contains(commitId)) {
        commitId = commitsIterator.next();
        commitsBehind++;
      }
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

    // Calculate downloads
    Map<String, Map<String, BuildTargetState>> currentSourcesState = myMetadataLoader.loadCurrentProjectMetadata();
    int totalDownloads = getLoaders(myProject).stream().mapToInt(loader -> loader.calculateDownloads(commitSourcesState, currentSourcesState)).sum();
    indicator.setFraction(0.01);

    try {
      // Computation with loaders results. If at least one of them failed rollback all job
      initLoaders(commitId, indicator, totalDownloads, commitSourcesState, currentSourcesState).thenAccept(loaderStatus -> {
        LOG.info("Loading finished with " + loaderStatus + " status");
        try {
          SegmentedProgressIndicatorManager indicatorManager = new SegmentedProgressIndicatorManager(indicator, totalDownloads, SEGMENT_SIZE);
          CompletableFuture.allOf(getLoaders(myProject).stream()
                                    .map(loader -> applyChanges(loaderStatus, loader, indicator, indicatorManager))
                                    .toArray(CompletableFuture[]::new))
            .thenRun(() -> saveStateAndNotify(loaderStatus, commitId, startTime))
            .get();
        }
        catch (InterruptedException | ExecutionException e) {
          LOG.warn("Unexpected exception rollback all progress", e);
          onFail();
          getLoaders(myProject).forEach(loader -> loader.rollback());
          indicator.setText("Rolling back downloaded caches");
        }
      }).handle((result, ex) -> handleExceptions(result, ex, indicator)).get();
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
    if (myWorkspaceConfiguration.MAKE_PROJECT_ON_SAVE) {
      LOG.warn("Project automatic build should be disabled, it can affect portable caches");
      return false;
    }
    hasRunningTask.set(true);
    return true;
  }

  private <T> CompletableFuture<LoaderStatus> initLoaders(String commitId, ProgressIndicator indicator, int totalDownloads,
                                                      Map<String, Map<String, BuildTargetState>> commitSourcesState,
                                                      Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    List<JpsOutputLoader<?>> loaders = getLoaders(myProject);

    // Create indicator with predefined segment size
    SegmentedProgressIndicatorManager downloadIndicatorManager = new SegmentedProgressIndicatorManager(indicator, totalDownloads, SEGMENT_SIZE);
    SegmentedProgressIndicatorManager extractIndicatorManager = new SegmentedProgressIndicatorManager(indicator, totalDownloads, SEGMENT_SIZE);
    JpsLoaderContext loaderContext = JpsLoaderContext.createNewContext(commitId, downloadIndicatorManager, commitSourcesState, currentSourcesState);

    // Start loaders with own context
    List<CompletableFuture<LoaderStatus>> completableFutures = ContainerUtil.map(loaders, loader ->
      CompletableFuture.supplyAsync(() -> loader.extract(loader.load(loaderContext), extractIndicatorManager), INSTANCE));

    // Reduce loaders statuses into the one
    CompletableFuture<LoaderStatus> initialFuture = completableFutures.get(0);
    if (completableFutures.size() > 1) {
      for (int i = 1; i < completableFutures.size(); i++) {
        initialFuture = initialFuture.thenCombine(completableFutures.get(i), JpsOutputLoaderManager::combine);
      }
    }
    return initialFuture;
  }

  private static CompletableFuture<Void> applyChanges(LoaderStatus loaderStatus, JpsOutputLoader<?> loader, ProgressIndicator indicator,
                                                      SegmentedProgressIndicatorManager indicatorManager) {
    if (loaderStatus == LoaderStatus.FAILED) {
      indicator.setText("Rolling back");
      return CompletableFuture.runAsync(() -> loader.rollback(), INSTANCE);
    }
    return CompletableFuture.runAsync(() -> loader.apply(indicatorManager), INSTANCE);
  }

  private void saveStateAndNotify(LoaderStatus loaderStatus, String commitId, long startTime) {
    if (loaderStatus == LoaderStatus.FAILED) {
      onFail();
      return;
    }

    PropertiesComponent.getInstance().setValue(LATEST_COMMIT_ID, commitId);
    BuildManager.getInstance().clearState(myProject);
    long endTime = (System.currentTimeMillis() - startTime) / 1000;
    ApplicationManager.getApplication().invokeLater(() -> {
      String message = "Update compiler caches completed successfully in " + endTime + " s";
      Notification notification = NONE_NOTIFICATION_GROUP.createNotification("Compiler Caches Loader", message,
                                                                             NotificationType.INFORMATION, null);
      Notifications.Bus.notify(notification, myProject);
    });
    LOG.info("Loading finished");
  }

  private Void handleExceptions(Void result, Throwable ex, ProgressIndicator indicator) {
    if (ex != null) {
      Throwable cause = ex.getCause();
      if (cause instanceof ProcessCanceledException) {
        LOG.info("Jps caches download canceled");
      }
      else {
        LOG.warn("Couldn't fetch jps compilation caches", ex);
        onFail();
      }
      getLoaders(myProject).forEach(loader -> loader.rollback());
      indicator.setText("Rolling back downloaded caches");
    }
    return result;
  }

  private List<JpsOutputLoader<?>> getLoaders(@NotNull Project project) {
    if (myJpsOutputLoadersLoaders != null) return myJpsOutputLoadersLoaders;
    myJpsOutputLoadersLoaders = Arrays.asList(new JpsCacheLoader(myServerClient, project),
                                              new JpsCompilationOutputLoader(myServerClient, project));
    return myJpsOutputLoadersLoaders;
  }

  private static LoaderStatus combine(LoaderStatus firstStatus, LoaderStatus secondStatus) {
    if (firstStatus == LoaderStatus.FAILED || secondStatus == LoaderStatus.FAILED) return LoaderStatus.FAILED;
    return LoaderStatus.COMPLETE;
  }

  private void onFail() {
    ApplicationManager.getApplication().invokeLater(() -> {
      Notification notification = NONE_NOTIFICATION_GROUP.createNotification("Compiler Caches Loader", "Update compiler caches failed",
                                                                             NotificationType.WARNING, null);
      Notifications.Bus.notify(notification, myProject);
    });
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefresher;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogStatusBarProgress;
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.ui.*;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VcsLogFiltererImpl;
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static com.intellij.vcs.log.impl.CustomVcsLogUiFactoryProvider.LOG_CUSTOM_UI_FACTORY_PROVIDER_EP;

public class VcsLogManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(VcsLogManager.class);

  protected final @NotNull Project myProject;
  private final @NotNull VcsLogTabsProperties myUiProperties;
  private final @Nullable BiConsumer<? super VcsLogErrorHandler.Source, ? super Throwable> myRecreateMainLogHandler;

  private final @NotNull VcsLogData myLogData;
  private final @NotNull VcsLogColorManager myColorManager;
  private @Nullable VcsLogTabsWatcher myTabsLogRefresher;
  private final @NotNull PostponableLogRefresher myPostponableRefresher;
  private final @NotNull VcsLogStatusBarProgress myStatusBarProgress;
  private boolean myDisposed;

  public VcsLogManager(@NotNull Project project, @NotNull VcsLogTabsProperties uiProperties, @NotNull Collection<VcsRoot> roots) {
    this(project, uiProperties, findLogProviders(roots, project), true, null);
  }

  public VcsLogManager(@NotNull Project project,
                       @NotNull VcsLogTabsProperties uiProperties,
                       @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                       boolean scheduleRefreshImmediately,
                       @Nullable BiConsumer<? super VcsLogErrorHandler.Source, ? super Throwable> recreateHandler) {
    myProject = project;
    myUiProperties = uiProperties;
    myRecreateMainLogHandler = recreateHandler;

    myLogData = new VcsLogData(myProject, logProviders, new MyErrorHandler(), this);
    myPostponableRefresher = new PostponableLogRefresher(myLogData);

    refreshLogOnVcsEvents(logProviders, myPostponableRefresher, myLogData);

    myColorManager = VcsLogColorManagerFactory.create(logProviders.keySet());
    myStatusBarProgress = new VcsLogStatusBarProgress(myProject, logProviders, myLogData.getIndex().getIndexingRoots(),
                                                      myLogData.getProgress());

    if (scheduleRefreshImmediately) {
      scheduleInitialization();
    }
  }

  @ApiStatus.Internal
  @CalledInAny
  public void scheduleInitialization() {
    myLogData.initialize();
  }

  @RequiresEdt
  public boolean isLogVisible() {
    return myPostponableRefresher.isLogVisible();
  }

  /**
   * Checks if this Log has a full data pack and there are no postponed refreshes. Does not check if there are refreshes in progress.
   */
  @ApiStatus.Internal
  @RequiresEdt
  public boolean isLogUpToDate() {
    return myLogData.getDataPack().isFull() && !myPostponableRefresher.hasPostponedRoots();
  }

  /**
   * Schedules Log initialization and update even when none on the log tabs is visible and a power save mode is enabled.
   *
   * @see PostponableLogRefresher#canRefreshNow()
   */
  @ApiStatus.Internal
  @RequiresEdt
  public void scheduleUpdate() {
    myLogData.initialize();
    myPostponableRefresher.refreshPostponedRoots();
  }

  public @NotNull VcsLogData getDataManager() {
    return myLogData;
  }

  public @NotNull VcsLogColorManager getColorManager() {
    return myColorManager;
  }

  public @NotNull VcsLogTabsProperties getUiProperties() {
    return myUiProperties;
  }

  public @NotNull MainVcsLogUi createLogUi(@NotNull String logId, @NotNull VcsLogTabLocation location) {
    return createLogUi(getMainLogUiFactory(logId, null), location, true);
  }

  @NotNull
  MainVcsLogUi createLogUi(@NotNull String logId, @NotNull VcsLogTabLocation location, boolean isClosedOnDispose) {
    return createLogUi(getMainLogUiFactory(logId, null), location, isClosedOnDispose);
  }

  public @NotNull VcsLogUiFactory<? extends MainVcsLogUi> getMainLogUiFactory(@NotNull String logId, @Nullable VcsLogFilterCollection filters) {
    CustomVcsLogUiFactoryProvider factoryProvider = LOG_CUSTOM_UI_FACTORY_PROVIDER_EP.findFirstSafe(myProject, p -> {
      return p.isActive(myLogData.getLogProviders());
    });
    if (factoryProvider == null) {
      return new MainVcsLogUiFactory(logId, filters, myUiProperties, myColorManager);
    }
    return factoryProvider.createLogUiFactory(logId, this, filters);
  }

  private @NotNull VcsLogTabsWatcher getTabsWatcher() {
    LOG.assertTrue(!myDisposed);
    if (myTabsLogRefresher == null) myTabsLogRefresher = new VcsLogTabsWatcher(myProject, myPostponableRefresher);
    return myTabsLogRefresher;
  }

  public @NotNull <U extends VcsLogUiEx> U createLogUi(@NotNull VcsLogUiFactory<U> factory, @NotNull VcsLogTabLocation location) {
    return createLogUi(factory, location, true);
  }

  private @NotNull <U extends VcsLogUiEx> U createLogUi(@NotNull VcsLogUiFactory<U> factory,
                                                        @NotNull VcsLogTabLocation location,
                                                        boolean isClosedOnDispose) {
    ThreadingAssertions.assertEventDispatchThread();
    if (isDisposed()) {
      LOG.error("Trying to create new VcsLogUi on a disposed VcsLogManager instance");
      throw new ProcessCanceledException();
    }

    U ui = factory.createLogUi(myProject, myLogData);
    Disposer.register(ui, getTabsWatcher().addTabToWatch(ui, location, isClosedOnDispose));

    return ui;
  }

  public @NotNull List<? extends VcsLogUi> getLogUis() {
    return getTabsWatcher().getTabs();
  }

  public @NotNull List<? extends VcsLogUi> getLogUis(@NotNull VcsLogTabLocation location) {
    return getTabsWatcher().getTabs(location);
  }

  public @NotNull List<? extends VcsLogUi> getVisibleLogUis(@NotNull VcsLogTabLocation location) {
    return getTabsWatcher().getVisibleTabs(location);
  }

  /*
   * For diagnostic purposes only
   */
  @ApiStatus.Internal
  public @NonNls String getLogWindowsInformation() {
    return myPostponableRefresher.getLogWindowsInformation();
  }

  private static void refreshLogOnVcsEvents(@NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                                            @NotNull PostponableLogRefresher refresher,
                                            @NotNull Disposable disposableParent) {
    MultiMap<VcsLogProvider, VirtualFile> providers2roots = MultiMap.create();
    logProviders.forEach((key, value) -> providers2roots.putValue(value, key));

    VcsLogRefresher wrappedRefresher = root -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        refresher.refresh(root);
      }, ModalityState.any());
    };
    for (Map.Entry<VcsLogProvider, Collection<VirtualFile>> entry : providers2roots.entrySet()) {
      Disposable disposable = entry.getKey().subscribeToRootRefreshEvents(entry.getValue(), wrappedRefresher);
      Disposer.register(disposableParent, disposable);
    }
  }

  public static @NotNull Map<VirtualFile, VcsLogProvider> findLogProviders(@NotNull Collection<VcsRoot> roots, @NotNull Project project) {
    if (roots.isEmpty()) return Collections.emptyMap();

    Map<VirtualFile, VcsLogProvider> logProviders = new HashMap<>();
    List<VcsLogProvider> allLogProviders = VcsLogProvider.LOG_PROVIDER_EP.getExtensionList(project);
    for (VcsRoot root : roots) {
      AbstractVcs vcs = root.getVcs();
      VirtualFile path = root.getPath();
      if (vcs == null) {
        LOG.debug("Skipping invalid VCS root: " + root);
        continue;
      }

      for (VcsLogProvider provider : allLogProviders) {
        if (provider.getSupportedVcs().equals(vcs.getKeyInstanceMethod())) {
          logProviders.put(path, provider);
          break;
        }
      }
    }
    return logProviders;
  }

  @RequiresEdt
  void disposeUi() {
    myDisposed = true;
    ThreadingAssertions.assertEventDispatchThread();
    if (myTabsLogRefresher != null) Disposer.dispose(myTabsLogRefresher);
    Disposer.dispose(myStatusBarProgress);
  }

  /**
   * Dispose VcsLogManager and execute some activity after it.
   *
   * @param callback activity to run after log is disposed. Is executed in background thread. null means execution of additional activity after disposing is not required.
   */
  @RequiresEdt
  public void dispose(@Nullable Runnable callback) {
    disposeUi();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Disposer.dispose(this);
      if (callback != null) {
        callback.run();
      }
    });
  }

  @Override
  @RequiresBackgroundThread
  public void dispose() {
    // since disposing log triggers flushing indexes on disk we do not want to do it in EDT
    // disposing of VcsLogManager is done by manually executing dispose(@Nullable Runnable callback)
    // the above method first disposes ui in EDT, then disposes everything else in a background
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    LOG.debug("Disposed Vcs Log for " + VcsLogUtil.getProvidersMapText(myLogData.getLogProviders()));
  }

  @RequiresEdt
  public boolean isDisposed() {
    return myDisposed;
  }

  private class MyErrorHandler implements VcsLogErrorHandler {
    private final @NotNull IntSet myErrors = IntSets.synchronize(new IntOpenHashSet());
    private final @NotNull AtomicBoolean myIsBroken = new AtomicBoolean(false);

    @Override
    public void handleError(@Nullable Source source, @NotNull Throwable throwable) {
      if (myIsBroken.compareAndSet(false, true)) {
        if (myRecreateMainLogHandler != null) {
          ApplicationManager.getApplication().invokeLater(() -> myRecreateMainLogHandler.accept(source, throwable));
        }
        else {
          LOG.error(source != null ? "Vcs Log exception from " + source : throwable.getMessage(), throwable);
        }

        if (source == Source.Storage) {
          ((VcsLogModifiableIndex)myLogData.getIndex()).markCorrupted();
        }
      }
      else {
        int errorHashCode = ThrowableInterner.computeTraceHashCode(throwable);
        if (myErrors.add(errorHashCode)) {
          LOG.debug("Vcs Log storage is broken and is being recreated", throwable);
        }
      }
    }

    @Override
    public void displayMessage(@Nls @NotNull String message) {
      VcsNotifier.getInstance(myProject).notifyError(VcsLogNotificationIdsHolder.FATAL_ERROR, "", message);
    }
  }

  @FunctionalInterface
  public interface VcsLogUiFactory<T extends VcsLogUiEx> {
    @ApiStatus.OverrideOnly
    T createLogUi(@NotNull Project project, @NotNull VcsLogData logData);
  }

  public abstract static class BaseVcsLogUiFactory<T extends VcsLogUiImpl> implements VcsLogUiFactory<T> {
    private final @NotNull String myLogId;
    private final @Nullable VcsLogFilterCollection myFilters;
    private final @NotNull VcsLogTabsProperties myUiProperties;
    private final @NotNull VcsLogColorManager myColorManager;

    public BaseVcsLogUiFactory(@NotNull String logId, @Nullable VcsLogFilterCollection filters, @NotNull VcsLogTabsProperties uiProperties,
                               @NotNull VcsLogColorManager colorManager) {
      myLogId = logId;
      myFilters = filters;
      myUiProperties = uiProperties;
      myColorManager = colorManager;
    }

    @Override
    public T createLogUi(@NotNull Project project, @NotNull VcsLogData logData) {
      MainVcsLogUiProperties properties = myUiProperties.createProperties(myLogId);
      VcsLogFiltererImpl vcsLogFilterer = new VcsLogFiltererImpl(logData);
      PermanentGraph.SortType initialSortType = properties.get(MainVcsLogUiProperties.BEK_SORT_TYPE);
      VcsLogFilterCollection initialFilters = myFilters == null ? VcsLogFilterObject.collection() : myFilters;
      VisiblePackRefresherImpl refresher = new VisiblePackRefresherImpl(project, logData, initialFilters, initialSortType,
                                                                        vcsLogFilterer, myLogId);
      return createVcsLogUiImpl(myLogId, logData, properties, myColorManager, refresher, myFilters);
    }

    protected abstract @NotNull T createVcsLogUiImpl(@NotNull String logId,
                                                     @NotNull VcsLogData logData,
                                                     @NotNull MainVcsLogUiProperties properties,
                                                     @NotNull VcsLogColorManager colorManager,
                                                     @NotNull VisiblePackRefresherImpl refresher,
                                                     @Nullable VcsLogFilterCollection filters);
  }

  private static class MainVcsLogUiFactory extends BaseVcsLogUiFactory<VcsLogUiImpl> {
    MainVcsLogUiFactory(@NotNull String logId, @Nullable VcsLogFilterCollection filters, @NotNull VcsLogTabsProperties properties,
                        @NotNull VcsLogColorManager colorManager) {
      super(logId, filters, properties, colorManager);
    }

    @Override
    protected @NotNull VcsLogUiImpl createVcsLogUiImpl(@NotNull String logId,
                                                       @NotNull VcsLogData logData,
                                                       @NotNull MainVcsLogUiProperties properties,
                                                       @NotNull VcsLogColorManager colorManager,
                                                       @NotNull VisiblePackRefresherImpl refresher,
                                                       @Nullable VcsLogFilterCollection filters) {
      return new VcsLogUiImpl(logId, logData, colorManager, properties, refresher, filters);
    }
  }
}

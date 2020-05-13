// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefresher;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogStatusBarProgress;
import com.intellij.vcs.log.data.VcsLogStorage;
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import com.intellij.vcs.log.ui.VcsLogUiEx;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VcsLogFiltererImpl;
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.vcs.log.impl.CustomVcsLogUiFactoryProvider.LOG_CUSTOM_UI_FACTORY_PROVIDER_EP;

public class VcsLogManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(VcsLogManager.class);

  @NotNull private final Project myProject;
  @NotNull private final VcsLogTabsProperties myUiProperties;
  @Nullable private final Consumer<? super Throwable> myRecreateMainLogHandler;

  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogColorManagerImpl myColorManager;
  @Nullable private VcsLogTabsWatcher myTabsLogRefresher;
  @NotNull private final PostponableLogRefresher myPostponableRefresher;
  @NotNull private final VcsLogStatusBarProgress myStatusBarProgress;
  private boolean myDisposed;

  public VcsLogManager(@NotNull Project project, @NotNull VcsLogTabsProperties uiProperties, @NotNull Collection<? extends VcsRoot> roots) {
    this(project, uiProperties, findLogProviders(roots, project), true, null);
  }

  public VcsLogManager(@NotNull Project project,
                       @NotNull VcsLogTabsProperties uiProperties,
                       @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                       boolean scheduleRefreshImmediately,
                       @Nullable Consumer<? super Throwable> recreateHandler) {
    myProject = project;
    myUiProperties = uiProperties;
    myRecreateMainLogHandler = recreateHandler;

    MyFatalErrorsHandler fatalErrorsHandler = new MyFatalErrorsHandler();
    myLogData = new VcsLogData(myProject, logProviders, fatalErrorsHandler, this);
    myPostponableRefresher = new PostponableLogRefresher(myLogData);

    refreshLogOnVcsEvents(logProviders, myPostponableRefresher, myLogData);

    myColorManager = new VcsLogColorManagerImpl(logProviders.keySet());
    myStatusBarProgress = new VcsLogStatusBarProgress(myProject, logProviders, myLogData.getProgress());

    if (scheduleRefreshImmediately) {
      scheduleInitialization();
    }
  }

  @CalledInAny
  public void scheduleInitialization() {
    myLogData.initialize();
  }

  @CalledInAwt
  public boolean isLogVisible() {
    return myPostponableRefresher.isLogVisible();
  }

  @NotNull
  public VcsLogData getDataManager() {
    return myLogData;
  }

  @NotNull
  public VcsLogColorManagerImpl getColorManager() {
    return myColorManager;
  }

  @NotNull
  public VcsLogTabsProperties getUiProperties() {
    return myUiProperties;
  }

  @NotNull
  public MainVcsLogUi createLogUi(@NotNull String logId, @NotNull LogWindowKind kind, boolean isClosedOnDispose) {
    return createLogUi(getMainLogUiFactory(logId, null), kind, isClosedOnDispose);
  }

  @NotNull
  public VcsLogUiFactory<? extends MainVcsLogUi> getMainLogUiFactory(@NotNull String logId, @Nullable VcsLogFilterCollection filters) {
    CustomVcsLogUiFactoryProvider factoryProvider = LOG_CUSTOM_UI_FACTORY_PROVIDER_EP.findFirstSafe(myProject, p -> p.isActive(this));
    if (factoryProvider == null) {
      return new MainVcsLogUiFactory(logId, filters);
    }
    return factoryProvider.createLogUiFactory(logId, this, filters);
  }

  @NotNull
  private VcsLogTabsWatcher getTabsWatcher() {
    LOG.assertTrue(!myDisposed);
    if (myTabsLogRefresher == null) myTabsLogRefresher = new VcsLogTabsWatcher(myProject, myPostponableRefresher);
    return myTabsLogRefresher;
  }

  @NotNull
  public <U extends VcsLogUiEx> U createLogUi(@NotNull VcsLogUiFactory<U> factory, @NotNull LogWindowKind kind) {
    return createLogUi(factory, kind, true);
  }

  @NotNull
  public <U extends VcsLogUiEx> U createLogUi(@NotNull VcsLogUiFactory<U> factory,
                                              @NotNull LogWindowKind kind,
                                              boolean isClosedOnDispose) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (isDisposed()) {
      LOG.error("Trying to create new VcsLogUi on a disposed VcsLogManager instance");
      throw new ProcessCanceledException();
    }

    U ui = factory.createLogUi(myProject, myLogData);
    Disposer.register(ui, getTabsWatcher().addTabToWatch(ui.getId(), ui.getRefresher(), kind, isClosedOnDispose));

    return ui;
  }

  /*
   * For diagnostic purposes only
   */
  @ApiStatus.Internal
  @NonNls
  public String getLogWindowsInformation() {
    return StringUtil.join(myPostponableRefresher.getLogWindows(),
                           window -> window.toString() + (window.isVisible() ? " (visible)" : ""), "\n");
  }

  private static void refreshLogOnVcsEvents(@NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                                            @NotNull VcsLogRefresher refresher,
                                            @NotNull Disposable disposableParent) {
    MultiMap<VcsLogProvider, VirtualFile> providers2roots = MultiMap.create();
    logProviders.forEach((key, value) -> providers2roots.putValue(value, key));

    for (Map.Entry<VcsLogProvider, Collection<VirtualFile>> entry : providers2roots.entrySet()) {
      Disposable disposable = entry.getKey().subscribeToRootRefreshEvents(entry.getValue(), refresher);
      Disposer.register(disposableParent, disposable);
    }
  }

  @NotNull
  public static Map<VirtualFile, VcsLogProvider> findLogProviders(@NotNull Collection<? extends VcsRoot> roots, @NotNull Project project) {
    if (roots.isEmpty()) return Collections.emptyMap();

    Map<VirtualFile, VcsLogProvider> logProviders = new HashMap<>();
    VcsLogProvider[] allLogProviders = VcsLogProvider.LOG_PROVIDER_EP.getExtensions(project);
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

  @CalledInAwt
  void disposeUi() {
    myDisposed = true;
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    if (myTabsLogRefresher != null) Disposer.dispose(myTabsLogRefresher);
    Disposer.dispose(myStatusBarProgress);
  }

  /**
   * Dispose VcsLogManager and execute some activity after it.
   *
   * @param callback activity to run after log is disposed. Is executed in background thread. null means execution of additional activity after dispose is not required.
   */
  @CalledInAwt
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
  public void dispose() {
    // since disposing log triggers flushing indexes on disk we do not want to do it in EDT
    // disposing of VcsLogManager is done by manually executing dispose(@Nullable Runnable callback)
    // the above method first disposes ui in EDT, than disposes everything else in background
    LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread());
    LOG.debug("Disposed Vcs Log for " + VcsLogUtil.getProvidersMapText(myLogData.getLogProviders()));
  }

  @CalledInAwt
  public boolean isDisposed() {
    return myDisposed;
  }

  private class MyFatalErrorsHandler implements FatalErrorHandler {
    private final AtomicBoolean myIsBroken = new AtomicBoolean(false);

    @Override
    public void consume(@Nullable Object source, @NotNull Throwable e) {
      if (myIsBroken.compareAndSet(false, true)) {
        processError(source, e);
      }
      else {
        LOG.debug("Vcs Log storage is broken and is being recreated", e);
      }
    }

    protected void processError(@Nullable Object source, @NotNull Throwable e) {
      if (myRecreateMainLogHandler != null) {
        ApplicationManager.getApplication().invokeLater(() -> myRecreateMainLogHandler.consume(e));
      }
      else {
        LOG.error(e);
      }

      if (source instanceof VcsLogStorage) {
        ((VcsLogModifiableIndex)myLogData.getIndex()).markCorrupted();
      }
    }

    @Override
    public void displayFatalErrorMessage(@Nls @NotNull String message) {
      VcsBalloonProblemNotifier.showOverChangesView(myProject, message, MessageType.ERROR);
    }
  }

  @FunctionalInterface
  public interface VcsLogUiFactory<T extends VcsLogUiEx> {
    T createLogUi(@NotNull Project project, @NotNull VcsLogData logData);
  }

  public abstract static class BaseVcsLogUiFactory<T extends VcsLogUiImpl> implements VcsLogUiFactory<T> {
    @NotNull private final String myLogId;
    @Nullable private final VcsLogFilterCollection myFilters;
    @NotNull private final VcsLogTabsProperties myUiProperties;
    @NotNull private final VcsLogColorManagerImpl myColorManager;

    public BaseVcsLogUiFactory(@NotNull String logId, @Nullable VcsLogFilterCollection filters, @NotNull VcsLogTabsProperties uiProperties,
                               @NotNull VcsLogColorManagerImpl colorManager) {
      myLogId = logId;
      myFilters = filters;
      myUiProperties = uiProperties;
      myColorManager = colorManager;
    }

    @Override
    public T createLogUi(@NotNull Project project,
                         @NotNull VcsLogData logData) {
      MainVcsLogUiProperties properties = myUiProperties.createProperties(myLogId);
      VcsLogFiltererImpl vcsLogFilterer = new VcsLogFiltererImpl(logData.getLogProviders(), logData.getStorage(),
                                                                 logData.getTopCommitsCache(),
                                                                 logData.getCommitDetailsGetter(), logData.getIndex());
      PermanentGraph.SortType initialSortType = properties.get(MainVcsLogUiProperties.BEK_SORT_TYPE);
      VcsLogFilterCollection initialFilters = myFilters == null ? VcsLogFilterObject.collection() : myFilters;
      VisiblePackRefresherImpl refresher = new VisiblePackRefresherImpl(project, logData, initialFilters, initialSortType,
                                                                        vcsLogFilterer, myLogId);
      return createVcsLogUiImpl(myLogId, logData, properties, myColorManager, refresher, myFilters);
    }

    @NotNull
    protected abstract T createVcsLogUiImpl(@NotNull String logId,
                                            @NotNull VcsLogData logData,
                                            @NotNull MainVcsLogUiProperties properties,
                                            @NotNull VcsLogColorManagerImpl colorManager,
                                            @NotNull VisiblePackRefresherImpl refresher,
                                            @Nullable VcsLogFilterCollection filters);
  }

  private class MainVcsLogUiFactory extends BaseVcsLogUiFactory<VcsLogUiImpl> {
    MainVcsLogUiFactory(@NotNull String logId, @Nullable VcsLogFilterCollection filters) {
      super(logId, filters, myUiProperties, myColorManager);
    }

    @Override
    @NotNull
    protected VcsLogUiImpl createVcsLogUiImpl(@NotNull String logId,
                                              @NotNull VcsLogData logData,
                                              @NotNull MainVcsLogUiProperties properties,
                                              @NotNull VcsLogColorManagerImpl colorManager,
                                              @NotNull VisiblePackRefresherImpl refresher,
                                              @Nullable VcsLogFilterCollection filters) {
      return new VcsLogUiImpl(logId, logData, colorManager, properties, refresher, filters);
    }
  }

  public enum LogWindowKind {
    TOOL_WINDOW,
    EDITOR,
    STANDALONE
  }
}

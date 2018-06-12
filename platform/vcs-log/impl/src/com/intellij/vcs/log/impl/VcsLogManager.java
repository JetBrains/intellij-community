// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefresher;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogStorage;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.visible.VcsLogFiltererImpl;
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class VcsLogManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(VcsLogManager.class);

  @NotNull private final Project myProject;
  @NotNull private final VcsLogTabsProperties myUiProperties;
  @Nullable private final Consumer<Throwable> myRecreateMainLogHandler;

  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogColorManagerImpl myColorManager;
  @NotNull private final VcsLogTabsWatcher myTabsLogRefresher;
  @NotNull private final PostponableLogRefresher myPostponableRefresher;

  public VcsLogManager(@NotNull Project project, @NotNull VcsLogTabsProperties uiProperties, @NotNull Collection<VcsRoot> roots) {
    this(project, uiProperties, roots, true, null);
  }

  public VcsLogManager(@NotNull Project project,
                       @NotNull VcsLogTabsProperties uiProperties,
                       @NotNull Collection<VcsRoot> roots,
                       boolean scheduleRefreshImmediately,
                       @Nullable Consumer<Throwable> recreateHandler) {
    myProject = project;
    myUiProperties = uiProperties;
    myRecreateMainLogHandler = recreateHandler;

    Map<VirtualFile, VcsLogProvider> logProviders = findLogProviders(roots, myProject);
    myLogData = new VcsLogData(myProject, logProviders, new MyFatalErrorsHandler(), this);
    myPostponableRefresher = new PostponableLogRefresher(myLogData);
    myTabsLogRefresher = new VcsLogTabsWatcher(myProject, myPostponableRefresher);

    refreshLogOnVcsEvents(logProviders, myPostponableRefresher, myLogData);

    myColorManager = new VcsLogColorManagerImpl(logProviders.keySet());

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
  public VcsLogUiImpl createLogUi(@NotNull String logId, boolean isToolWindowTab) {
    return createLogUi(getMainLogUiFactory(logId), isToolWindowTab);
  }

  @NotNull
  public VcsLogUiFactory<? extends VcsLogUiImpl> getMainLogUiFactory(@NotNull String logId) {
    return new MainVcsLogUiFactory(logId);
  }

  @NotNull
  public <U extends AbstractVcsLogUi> U createLogUi(@NotNull VcsLogUiFactory<U> factory, boolean isToolWindowTab) {
    U ui = factory.createLogUi(myProject, myLogData);

    Disposable disposable;
    if (isToolWindowTab) {
      disposable = myTabsLogRefresher.addTabToWatch(ui.getId(), ui.getRefresher());
    }
    else {
      disposable = myPostponableRefresher.addLogWindow(ui.getRefresher());
    }
    Disposer.register(ui, disposable);

    ui.requestFocus();
    return ui;
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
  public static Map<VirtualFile, VcsLogProvider> findLogProviders(@NotNull Collection<VcsRoot> roots, @NotNull Project project) {
    Map<VirtualFile, VcsLogProvider> logProviders = ContainerUtil.newHashMap();
    VcsLogProvider[] allLogProviders = Extensions.getExtensions(VcsLogProvider.LOG_PROVIDER_EP, project);
    for (VcsRoot root : roots) {
      AbstractVcs vcs = root.getVcs();
      VirtualFile path = root.getPath();
      if (vcs == null || path == null) {
        LOG.error("Skipping invalid VCS root: " + root);
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

  /**
   * Dispose VcsLogManager and execute some activity after it.
   *
   * @param callback activity to run after log is disposed. Is executed in background thread. null means execution of additional activity after dispose is not required.
   */
  @CalledInAwt
  public void dispose(@Nullable Runnable callback) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

    myTabsLogRefresher.closeLogTabs();

    Disposer.dispose(myTabsLogRefresher);
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
        myLogData.getIndex().markCorrupted();
      }
    }

    @Override
    public void displayFatalErrorMessage(@NotNull String message) {
      VcsBalloonProblemNotifier.showOverChangesView(myProject, message, MessageType.ERROR);
    }
  }

  @FunctionalInterface
  public interface VcsLogUiFactory<T extends AbstractVcsLogUi> {
    T createLogUi(@NotNull Project project, @NotNull VcsLogData logData);
  }

  private class MainVcsLogUiFactory implements VcsLogUiFactory<VcsLogUiImpl> {
    private final String myLogId;

    public MainVcsLogUiFactory(@NotNull String logId) {
      myLogId = logId;
    }

    @Override
    public VcsLogUiImpl createLogUi(@NotNull Project project,
                                    @NotNull VcsLogData logData) {
      MainVcsLogUiProperties properties = myUiProperties.createProperties(myLogId);
      VisiblePackRefresherImpl refresher =
        new VisiblePackRefresherImpl(project, logData, properties.get(MainVcsLogUiProperties.BEK_SORT_TYPE),
                                     new VcsLogFiltererImpl(logData.getLogProviders(), logData.getStorage(),
                                                            logData.getTopCommitsCache(),
                                                            logData.getCommitDetailsGetter(), logData.getIndex()), myLogId);
      return new VcsLogUiImpl(myLogId, logData, myColorManager, properties, refresher);
    }
  }
}

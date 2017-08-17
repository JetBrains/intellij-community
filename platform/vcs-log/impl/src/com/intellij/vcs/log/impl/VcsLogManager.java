/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.vcs.log.ui.*;
import com.intellij.vcs.log.visible.VcsLogFilterer;
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
  private boolean myInitialized = false;

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

  @CalledInAwt
  public void scheduleInitialization() {
    if (!myInitialized) {
      myInitialized = true;
      myLogData.initialize();
    }
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
  public JComponent createLogPanel(@NotNull String logId, @Nullable String contentTabName) {
    AbstractVcsLogUi ui = createLogUi(logId, contentTabName);
    return new VcsLogPanel(this, ui);
  }

  @NotNull
  public VcsLogUiImpl createLogUi(@NotNull String logId, @Nullable String contentTabName) {
    return createLogUi(contentTabName, getMainLogUiFactory(logId));
  }

  @NotNull
  public VcsLogUiFactory<? extends VcsLogUiImpl> getMainLogUiFactory(@NotNull String logId) {
    return new MainVcsLogUiFactory(logId);
  }

  @NotNull
  public <U extends AbstractVcsLogUi> U createLogUi(@Nullable String contentTabName,
                                                    @NotNull VcsLogUiFactory<U> factory) {
    U ui = factory.createLogUi(myProject, myLogData, myColorManager);

    Disposable disposable;
    if (contentTabName != null) {
      disposable = myTabsLogRefresher.addTabToWatch(contentTabName, ui.getRefresher());
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

  public void disposeUi() {
    myTabsLogRefresher.closeLogTabs();
    Disposer.dispose(myTabsLogRefresher);
  }

  @Override
  public void dispose() {
  }

  private class MyFatalErrorsHandler implements FatalErrorHandler {
    private final AtomicBoolean myIsBroken = new AtomicBoolean(false);

    @Override
    public void consume(@Nullable Object source, @NotNull Exception e) {
      if (myIsBroken.compareAndSet(false, true)) {
        processError(source, e);
      }
      else {
        LOG.debug("Vcs Log storage is broken and is being recreated", e);
      }
    }

    protected void processError(@Nullable Object source, @NotNull Exception e) {
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
    T createLogUi(@NotNull Project project, @NotNull VcsLogData logData,
                  @NotNull VcsLogColorManager colorManager);
  }

  private class MainVcsLogUiFactory implements VcsLogUiFactory<VcsLogUiImpl> {
    private final String myLogId;

    public MainVcsLogUiFactory(@NotNull String logId) {
      myLogId = logId;
    }

    @Override
    public VcsLogUiImpl createLogUi(@NotNull Project project,
                                    @NotNull VcsLogData logData,
                                    @NotNull VcsLogColorManager manager) {
      MainVcsLogUiProperties properties = myUiProperties.createProperties(myLogId);
      VisiblePackRefresherImpl refresher =
        new VisiblePackRefresherImpl(project, logData, properties.get(MainVcsLogUiProperties.BEK_SORT_TYPE),
                                     new VcsLogFilterer(logData.getLogProviders(), logData.getStorage(),
                                                        logData.getTopCommitsCache(),
                                                        logData.getCommitDetailsGetter(), logData.getIndex()));
      return new VcsLogUiImpl(logData, project, manager, properties, refresher);
    }
  }
}

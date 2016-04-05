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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.data.*;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public class VcsLogManager implements Disposable {

  public static final ExtensionPointName<VcsLogProvider> LOG_PROVIDER_EP = ExtensionPointName.create("com.intellij.logProvider");
  private static final Logger LOG = Logger.getInstance(VcsLogManager.class);

  @NotNull private final Project myProject;
  @NotNull private final VcsLogTabsProperties myUiProperties;

  @Nullable private Runnable myRecreateMainLogHandler;

  private volatile VcsLogUiImpl myUi;
  private VcsLogDataManager myDataManager;
  private VcsLogColorManagerImpl myColorManager;
  private VcsLogTabsRefresher myTabsLogRefresher;

  public VcsLogManager(@NotNull Project project, @NotNull VcsLogTabsProperties uiProperties) {
    myProject = project;
    myUiProperties = uiProperties;
  }

  public VcsLogDataManager getDataManager() {
    return myDataManager;
  }

  @NotNull
  protected Collection<VcsRoot> getVcsRoots() {
    return Arrays.asList(ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots());
  }

  public void watchTab(@NotNull final String contentTabName, @NotNull VcsLogUiImpl logUi) {
    myTabsLogRefresher.addTabToWatch(contentTabName, logUi.getFilterer());
    Disposer.register(logUi, new Disposable() {
      @Override
      public void dispose() {
        unwatchTab(contentTabName);
      }
    });
  }

  public void unwatchTab(@NotNull String contentTabName) {
    myTabsLogRefresher.removeTabFromWatch(contentTabName);
  }

  private void watch(@NotNull final VcsLogUiImpl ui) {
    final DataPackChangeListener listener = new DataPackChangeListener() {
      @Override
      public void onDataPackChange(@NotNull DataPack dataPack) {
        ui.getFilterer().onRefresh();
      }
    };
    myDataManager.addDataPackChangeListener(listener);
    Disposer.register(ui, new Disposable() {
      @Override
      public void dispose() {
        myDataManager.removeDataPackChangeListener(listener);
      }
    });
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ui.getFilterer().onRefresh();
      }
    });
  }

  @NotNull
  public JComponent initMainLog(@Nullable String contentTabName) {
    myUi = createLog(VcsLogTabsProperties.MAIN_LOG_ID);
    if (contentTabName != null) {
      watchTab(contentTabName, myUi);
    }
    else {
      watch(myUi);
    }
    myUi.requestFocus();
    return myUi.getMainFrame().getMainComponent();
  }

  @NotNull
  public VcsLogUiImpl createLog(@NotNull String logId) {
    initData();

    VcsLogUiProperties properties = myUiProperties.createProperties(logId);
    VcsLogFiltererImpl filterer =
      new VcsLogFiltererImpl(myProject, myDataManager, PermanentGraph.SortType.values()[properties.getBekSortType()]);
    return new VcsLogUiImpl(myDataManager, myProject, myColorManager, properties, filterer);
  }

  public boolean initData() {
    if (myDataManager != null) return true;

    Map<VirtualFile, VcsLogProvider> logProviders = findLogProviders(getVcsRoots(), myProject);
    myDataManager = new VcsLogDataManager(myProject, logProviders, new MyFatalErrorsConsumer());
    myTabsLogRefresher = new VcsLogTabsRefresher(myProject, myDataManager);

    refreshLogOnVcsEvents(logProviders, myTabsLogRefresher);

    myColorManager = new VcsLogColorManagerImpl(logProviders.keySet());

    myDataManager.refreshCompletely();
    return false;
  }

  private static void refreshLogOnVcsEvents(@NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                                            @NotNull VcsLogTabsRefresher refresher) {
    MultiMap<VcsLogProvider, VirtualFile> providers2roots = MultiMap.create();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : logProviders.entrySet()) {
      providers2roots.putValue(entry.getValue(), entry.getKey());
    }

    for (Map.Entry<VcsLogProvider, Collection<VirtualFile>> entry : providers2roots.entrySet()) {
      Disposable disposable = entry.getKey().subscribeToRootRefreshEvents(entry.getValue(), refresher);
      Disposer.register(refresher, disposable);
    }
  }

  @NotNull
  public static Map<VirtualFile, VcsLogProvider> findLogProviders(@NotNull Collection<VcsRoot> roots, @NotNull Project project) {
    Map<VirtualFile, VcsLogProvider> logProviders = ContainerUtil.newHashMap();
    VcsLogProvider[] allLogProviders = Extensions.getExtensions(LOG_PROVIDER_EP, project);
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

  public void setRecreateMainLogHandler(@Nullable Runnable recreateMainLogHandler) {
    myRecreateMainLogHandler = recreateMainLogHandler;
  }

  /**
   * The instance of the {@link VcsLogUiImpl} or null if the log was not initialized yet.
   */
  @Nullable
  public VcsLogUiImpl getMainLogUi() {
    return myUi;
  }

  public void disposeLog() {
    if (myDataManager != null) Disposer.dispose(myDataManager);

    myDataManager = null;
    myTabsLogRefresher = null;
    myColorManager = null;
    myUi = null;
  }

  public static VcsLogManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, VcsLogManager.class);
  }

  @Override
  public void dispose() {
    disposeLog();
  }

  private class MyFatalErrorsConsumer implements Consumer<Exception> {
    private boolean myIsBroken = false;

    @Override
    public void consume(@NotNull final Exception e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (!myIsBroken) {
            myIsBroken = true;
            processErrorFirstTime(e);
          }
          else {
            LOG.debug(e);
          }
        }
      });
    }

    protected void processErrorFirstTime(@NotNull Exception e) {
      if (myRecreateMainLogHandler != null) {
        LOG.info(e);
        VcsBalloonProblemNotifier.showOverChangesView(myProject, "Fatal error, VCS Log recreated: " + e.getMessage(), MessageType.ERROR);
        myRecreateMainLogHandler.run();
      }
      else {
        LOG.error(e);
      }
    }
  }
}

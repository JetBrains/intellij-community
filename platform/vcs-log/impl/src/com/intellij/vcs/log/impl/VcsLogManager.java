/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefresher;
import com.intellij.vcs.log.data.VcsLogDataManager;
import com.intellij.vcs.log.data.VcsLogFiltererImpl;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class VcsLogManager implements Disposable {

  public static final ExtensionPointName<VcsLogProvider> LOG_PROVIDER_EP = ExtensionPointName.create("com.intellij.logProvider");
  private static final Logger LOG = Logger.getInstance(VcsLogManager.class);

  @NotNull private final Project myProject;
  @NotNull private final VcsLogUiProperties myUiProperties;

  private volatile VcsLogUiImpl myUi;
  private VcsLogDataManager myDataManager;
  private VcsLogColorManagerImpl myColorManager;
  private PostponableLogRefresher myLogRefresher;

  public VcsLogManager(@NotNull Project project, @NotNull VcsLogUiProperties uiProperties) {
    myProject = project;
    myUiProperties = uiProperties;

    Disposer.register(project, this);
  }

  public VcsLogDataManager getDataManager() {
    return myDataManager;
  }

  @NotNull
  protected Collection<VcsRoot> getVcsRoots() {
    return Arrays.asList(ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots());
  }

  public void watchTab(@NotNull String contentTabName) {
    myLogRefresher.addTabToWatch(contentTabName);
  }

  public void unwatchTab(@NotNull String contentTabName) {
    myLogRefresher.removeTabToWatch(contentTabName);
  }

  @NotNull
  public JComponent initMainLog(@Nullable String contentTabName) {
    initData();

    VcsLogUiImpl ui = new VcsLogUiImpl(myDataManager, myProject, myColorManager, myUiProperties,
                                       new VcsLogFiltererImpl(myProject, myDataManager,
                                                              PermanentGraph.SortType.values()[myUiProperties.getBekSortType()]));
    if (contentTabName != null) {
      watchTab(contentTabName);
    }
    myUi = ui;
    myUi.requestFocus();
    return myUi.getMainFrame().getMainComponent();
  }

  public boolean initData() {
    if (myDataManager != null) return true;

    Map<VirtualFile, VcsLogProvider> logProviders = findLogProviders(getVcsRoots(), myProject);
    myDataManager = new VcsLogDataManager(myProject, logProviders);
    myLogRefresher = new PostponableLogRefresher(myProject, myDataManager);

    refreshLogOnVcsEvents(logProviders, myLogRefresher);

    myColorManager = new VcsLogColorManagerImpl(logProviders.keySet());

    myDataManager.refreshCompletely();
    return false;
  }

  private static void refreshLogOnVcsEvents(@NotNull Map<VirtualFile, VcsLogProvider> logProviders, @NotNull VcsLogRefresher refresher) {
    MultiMap<VcsLogProvider, VirtualFile> providers2roots = MultiMap.create();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : logProviders.entrySet()) {
      providers2roots.putValue(entry.getValue(), entry.getKey());
    }

    for (Map.Entry<VcsLogProvider, Collection<VirtualFile>> entry : providers2roots.entrySet()) {
      entry.getKey().subscribeToRootRefreshEvents(entry.getValue(), refresher);
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
    myLogRefresher = null;
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

  private static class PostponableLogRefresher implements VcsLogRefresher, Disposable {

    private static final String TOOLWINDOW_ID = ChangesViewContentManager.TOOLWINDOW_ID;

    @NotNull private final VcsLogDataManager myDataManager;
    @NotNull private final ToolWindowManagerImpl myToolWindowManager;
    @NotNull private final ToolWindowImpl myToolWindow;
    @NotNull private final Set<String> myTabs = ContainerUtil.newHashSet();
    @NotNull private final MyRefreshPostponedEventsListener myPostponedEventsListener;

    @NotNull private final Set<VirtualFile> myRootsToRefresh = ContainerUtil.newConcurrentSet();

    public PostponableLogRefresher(@NotNull Project project, @NotNull VcsLogDataManager dataManager) {
      myDataManager = dataManager;
      myToolWindowManager = (ToolWindowManagerImpl)ToolWindowManager.getInstance(project);
      myToolWindow = (ToolWindowImpl)myToolWindowManager.getToolWindow(TOOLWINDOW_ID);

      Disposer.register(dataManager, this);

      myPostponedEventsListener = new MyRefreshPostponedEventsListener();
      myToolWindow.getContentManager().addContentManagerListener(myPostponedEventsListener);
      myToolWindowManager.addToolWindowManagerListener(myPostponedEventsListener);
    }

    public void addTabToWatch(@NotNull String tabName) {
      myTabs.add(tabName);
    }

    public void removeTabToWatch(@NotNull String tabName) {
      myTabs.remove(tabName);
    }

    @Override
    public void refresh(@NotNull VirtualFile root) {
      if (isRefreshEnabled()) {
        myDataManager.refresh(Collections.singleton(root));
      }
      else {
        myRootsToRefresh.add(root);
      }
    }

    @Override
    public void dispose() {
      myToolWindow.getContentManager().removeContentManagerListener(myPostponedEventsListener);
      myToolWindowManager.removeToolWindowManagerListener(myPostponedEventsListener);
    }

    private boolean isRefreshEnabled() {
      if (myTabs.isEmpty()) return true;
      if (myToolWindowManager.isToolWindowRegistered(TOOLWINDOW_ID) && myToolWindow.isVisible()) {
        Content content = myToolWindow.getContentManager().getSelectedContent();
        return content != null && myTabs.contains(content.getTabName());
      }
      return false;
    }

    private void refreshPostponedRoots() {
      Set<VirtualFile> toRefresh = new HashSet<VirtualFile>(myRootsToRefresh);
      myRootsToRefresh.removeAll(toRefresh); // clear the set, but keep roots which could possibly arrive after collecting them in the var.
      myDataManager.refresh(toRefresh);
    }

    private class MyRefreshPostponedEventsListener extends ContentManagerAdapter implements ToolWindowManagerListener {

      @Override
      public void selectionChanged(ContentManagerEvent event) {
        refreshRootsIfNeeded();
      }

      @Override
      public void stateChanged() {
        refreshRootsIfNeeded();
      }

      @Override
      public void toolWindowRegistered(@NotNull String id) {
      }

      private void refreshRootsIfNeeded() {
        if (isRefreshEnabled()) {
          refreshPostponedRoots();
        }
      }
    }
  }

}

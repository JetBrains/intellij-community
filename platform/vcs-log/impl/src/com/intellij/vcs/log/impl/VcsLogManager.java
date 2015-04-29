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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefresher;
import com.intellij.vcs.log.VcsLogSettings;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class VcsLogManager implements Disposable {

  public static final ExtensionPointName<VcsLogProvider> LOG_PROVIDER_EP = ExtensionPointName.create("com.intellij.logProvider");
  private static final Logger LOG = Logger.getInstance(VcsLogManager.class);

  @NotNull private final Project myProject;
  @NotNull private final VcsLogSettings mySettings;
  @NotNull private final VcsLogUiProperties myUiProperties;

  private volatile VcsLogUiImpl myUi;

  public VcsLogManager(@NotNull Project project,
                       @NotNull VcsLogSettings settings,
                       @NotNull VcsLogUiProperties uiProperties) {
    myProject = project;
    mySettings = settings;
    myUiProperties = uiProperties;
  }

  @NotNull
  public JComponent initContent(@NotNull Collection<VcsRoot> roots, @Nullable String contentTabName) {
    Disposer.register(myProject, this);

    final Map<VirtualFile, VcsLogProvider> logProviders = findLogProviders(roots, myProject);

    Consumer<VisiblePack> visiblePackConsumer = new Consumer<VisiblePack>() {
      @Override
      public void consume(final VisiblePack pack) {
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              if (myUi != null && !Disposer.isDisposed(myUi)) {
                myUi.setVisiblePack(pack);
              }
            }
          });
      }
    };
    final VcsLogDataHolder logDataHolder = new VcsLogDataHolder(myProject, this, logProviders, mySettings, myUiProperties, visiblePackConsumer);
    myUi = new VcsLogUiImpl(logDataHolder, myProject, mySettings,
                            new VcsLogColorManagerImpl(logProviders.keySet()), myUiProperties, logDataHolder.getFilterer());
    myUi.addLogListener(logDataHolder.getContainingBranchesGetter()); // TODO: remove this after VcsLogDataHolder vs VcsLoUi dependency cycle is solved
    VcsLogRefresher logRefresher;
    if (contentTabName != null) {
      logRefresher = new PostponableLogRefresher(myProject, logDataHolder, contentTabName);
    }
    else {
      logRefresher = new VcsLogRefresher() {
        @Override
        public void refresh(@NotNull VirtualFile root) {
          logDataHolder.refresh(Collections.singletonList(root));
        }
      };
    }
    refreshLogOnVcsEvents(logProviders, logRefresher);
    logDataHolder.initialize();

    // todo fix selection
    final VcsLogGraphTable graphTable = myUi.getTable();
    if (graphTable.getRowCount() > 0) {
      IdeFocusManager.getInstance(myProject).requestFocus(graphTable, true).doWhenProcessed(new Runnable() {
        @Override
        public void run() {
          graphTable.setRowSelectionInterval(0, 0);
        }
      });
    }
    return myUi.getMainFrame().getMainComponent();
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
   * The instance of the {@link com.intellij.vcs.log.ui.VcsLogUiImpl} or null if the log was not initialized yet.
   */
  @Nullable
  public VcsLogUiImpl getLogUi() {
    return myUi;
  }

  @Override
  public void dispose() {
    myUi = null;
  }

  private static class PostponableLogRefresher implements VcsLogRefresher, Disposable {

    private static final String TOOLWINDOW_ID = ChangesViewContentManager.TOOLWINDOW_ID;

    @NotNull private final VcsLogDataHolder myDataHolder;
    @NotNull private final ToolWindowManagerImpl myToolWindowManager;
    @NotNull private final ToolWindowImpl myToolWindow;
    @NotNull private final String myTabName;
    @NotNull private final MyRefreshPostponedEventsListener myPostponedEventsListener;

    @NotNull private final Set<VirtualFile> myRootsToRefresh = ContainerUtil.newConcurrentSet();

    public PostponableLogRefresher(@NotNull Project project, @NotNull VcsLogDataHolder dataHolder, @NotNull String contentTabName) {
      myDataHolder = dataHolder;
      myToolWindowManager = (ToolWindowManagerImpl)ToolWindowManager.getInstance(project);
      myToolWindow = (ToolWindowImpl)myToolWindowManager.getToolWindow(TOOLWINDOW_ID);
      myTabName = contentTabName;

      Disposer.register(dataHolder, this);

      myPostponedEventsListener = new MyRefreshPostponedEventsListener();
      myToolWindow.getContentManager().addContentManagerListener(myPostponedEventsListener);
      myToolWindowManager.addToolWindowManagerListener(myPostponedEventsListener);
    }

    @Override
    public void refresh(@NotNull VirtualFile root) {
      if (isOurContentPaneShowing()) {
        myDataHolder.refresh(Collections.singleton(root));
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

    private boolean isOurContentPaneShowing() {
      if (myToolWindowManager.isToolWindowRegistered(TOOLWINDOW_ID) && myToolWindow.isVisible()) {
        Content content = myToolWindow.getContentManager().getSelectedContent();
        return content != null && content.getTabName().equals(myTabName);
      }
      return false;
    }

    private void refreshPostponedRoots() {
      Set<VirtualFile> toRefresh = new HashSet<VirtualFile>(myRootsToRefresh);
      myRootsToRefresh.removeAll(toRefresh); // clear the set, but keep roots which could possibly arrive after collecting them in the var.
      myDataHolder.refresh(toRefresh);
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
        if (isOurContentPaneShowing()) {
          refreshPostponedRoots();
        }
      }
    }
  }

}

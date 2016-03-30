/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogRefresher;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.DataPackChangeListener;
import com.intellij.vcs.log.data.VcsLogDataManager;
import com.intellij.vcs.log.data.VcsLogFilterer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VcsLogTabsRefresher implements VcsLogRefresher, Disposable {
  private static final String TOOLWINDOW_ID = ChangesViewContentManager.TOOLWINDOW_ID;

  @NotNull private final VcsLogDataManager myDataManager;
  @NotNull private final ToolWindowManagerImpl myToolWindowManager;
  @NotNull private final ToolWindowImpl myToolWindow;
  @NotNull private final MyRefreshPostponedEventsListener myPostponedEventsListener;

  @NotNull private final Map<String, VcsLogFilterer> myTabToFiltererMap = ContainerUtil.newHashMap();
  @NotNull private final Set<VirtualFile> myRootsToRefresh = ContainerUtil.newHashSet();

  public VcsLogTabsRefresher(@NotNull Project project, @NotNull VcsLogDataManager dataManager) {
    myDataManager = dataManager;
    myToolWindowManager = (ToolWindowManagerImpl)ToolWindowManager.getInstance(project);
    myToolWindow = ObjectUtils.assertNotNull((ToolWindowImpl)myToolWindowManager.getToolWindow(TOOLWINDOW_ID));

    Disposer.register(dataManager, this);

    myPostponedEventsListener = new MyRefreshPostponedEventsListener();
    myToolWindow.getContentManager().addContentManagerListener(myPostponedEventsListener);
    myToolWindowManager.addToolWindowManagerListener(myPostponedEventsListener);

    myDataManager.addDataPackChangeListener(new DataPackChangeListener() {
      @Override
      public void onDataPackChange(@NotNull DataPack dataPack) {
        for (Map.Entry<String, VcsLogFilterer> tabAndFilterer : myTabToFiltererMap.entrySet()) {
          dataPackArrived(tabAndFilterer.getValue(), isTabVisible(tabAndFilterer.getKey()));
        }
      }
    });
  }

  private void tabActivated(@NotNull VcsLogFilterer filterer, boolean firstTime) {
    if (!myRootsToRefresh.isEmpty()) {
      refreshPostponedRoots();
    }
    else {
      if (!filterer.isValid() || firstTime) {
        filterer.onRefresh();
      }
    }
  }

  private static void dataPackArrived(@NotNull VcsLogFilterer filterer, boolean visible) {
    if (visible) {
      filterer.onRefresh();
    }
    else {
      filterer.invalidate();
    }
  }

  @Override
  public void refresh(@NotNull final VirtualFile root) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (isOneOfTabsVisible()) {
          myDataManager.refresh(Collections.singleton(root));
        }
        else {
          myRootsToRefresh.add(root);
        }
      }
    }, ModalityState.any());
  }

  private void refreshPostponedRoots() {
    Set<VirtualFile> toRefresh = new HashSet<VirtualFile>(myRootsToRefresh);
    myRootsToRefresh.removeAll(toRefresh); // clear the set, but keep roots which could possibly arrive after collecting them in the var.
    myDataManager.refresh(toRefresh);
  }

  private boolean isOneOfTabsVisible() {
    return isOneOfTabsVisible(myTabToFiltererMap.keySet());
  }

  protected boolean isTabVisible(@NotNull String tab) {
    return isOneOfTabsVisible(Collections.singleton(tab));
  }

  private boolean isOneOfTabsVisible(@NotNull Set<String> tabs) {
    if (tabs.isEmpty()) return true;
    // probably, each log ui should determine where it is located (not necessarily in Log window tool tabs) and when it is visible
    String selectedTab = getSelectedTabName();
    return selectedTab != null && tabs.contains(selectedTab);
  }

  @Nullable
  private String getSelectedTabName() {
    if (myToolWindowManager.isToolWindowRegistered(TOOLWINDOW_ID) && myToolWindow.isVisible()) {
      Content content = myToolWindow.getContentManager().getSelectedContent();
      if (content != null) {
        return content.getTabName();
      }
    }
    return null;
  }

  public void addTabToWatch(@NotNull String contentTabName, @NotNull VcsLogFilterer filterer) {
    myTabToFiltererMap.put(contentTabName, filterer);
    tabActivated(filterer, true);
  }

  public void removeTabFromWatch(@NotNull String contentTabName) {
    myTabToFiltererMap.remove(contentTabName);
  }

  @Override
  public void dispose() {
    myToolWindow.getContentManager().removeContentManagerListener(myPostponedEventsListener);
    myToolWindowManager.removeToolWindowManagerListener(myPostponedEventsListener);
  }

  private class MyRefreshPostponedEventsListener extends ContentManagerAdapter
    implements ToolWindowManagerListener, PropertyChangeListener {

    private void selectionChanged() {
      String tabName = getSelectedTabName();
      if (tabName != null) {
        selectionChanged(tabName);
      }
    }

    private void selectionChanged(String tabName) {
      VcsLogFilterer filterer = myTabToFiltererMap.get(tabName);
      if (filterer != null) {
        tabActivated(filterer, false);
      }
    }

    @Override
    public void selectionChanged(ContentManagerEvent event) {
      if (ContentManagerEvent.ContentOperation.add.equals(event.getOperation())) {
        selectionChanged(event.getContent().getTabName());
      }
    }

    @Override
    public void contentAdded(ContentManagerEvent event) {
      Content content = event.getContent();
      if (content instanceof TabbedContent) {
        content.addPropertyChangeListener(this);
      }
    }

    @Override
    public void contentRemoved(ContentManagerEvent event) {
      Content content = event.getContent();
      if (content instanceof TabbedContent) {
        content.removePropertyChangeListener(this);
      }
    }

    @Override
    public void stateChanged() {
      selectionChanged();
    }

    @Override
    public void toolWindowRegistered(@NotNull String id) {
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      if (evt.getPropertyName().equals(Content.PROP_COMPONENT)) {
        selectionChanged();
      }
    }
  }
}

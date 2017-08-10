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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.impl.PostponableLogRefresher.VcsLogWindow;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Set;

public class VcsLogTabsWatcher implements Disposable {
  private static final String TOOLWINDOW_ID = ChangesViewContentManager.TOOLWINDOW_ID;

  @NotNull private final PostponableLogRefresher myRefresher;

  @NotNull private final ToolWindowManagerEx myToolWindowManager;
  @NotNull private final MyRefreshPostponedEventsListener myPostponedEventsListener;
  @Nullable private ToolWindow myToolWindow;

  public VcsLogTabsWatcher(@NotNull Project project, @NotNull PostponableLogRefresher refresher, @NotNull Disposable parentDisposable) {
    myRefresher = refresher;
    myToolWindowManager = ToolWindowManagerEx.getInstanceEx(project);

    myPostponedEventsListener = new MyRefreshPostponedEventsListener();
    ApplicationManager.getApplication().invokeLater(() -> {
      myToolWindowManager.addToolWindowManagerListener(myPostponedEventsListener);
      installContentListener();
    });

    Disposer.register(parentDisposable, this);
  }

  @Nullable
  private String getSelectedTabName() {
    if (myToolWindow != null && myToolWindow.isVisible()) {
      Content content = myToolWindow.getContentManager().getSelectedContent();
      if (content != null) {
        return content.getTabName();
      }
    }
    return null;
  }

  @NotNull
  public Disposable addTabToWatch(@NotNull String contentTabName, @NotNull VisiblePackRefresher refresher) {
    return myRefresher.addLogWindow(new VcsLogTab(refresher, contentTabName));
  }

  private void installContentListener() {
    ToolWindow window = myToolWindowManager.getToolWindow(TOOLWINDOW_ID);
    if (window != null) {
      myToolWindow = window;
      myToolWindow.getContentManager().addContentManagerListener(myPostponedEventsListener);
    }
  }

  @Override
  public void dispose() {
    removeListeners();
  }

  private void removeListeners() {
    myToolWindowManager.removeToolWindowManagerListener(myPostponedEventsListener);

    if (myToolWindow != null) {
      myToolWindow.getContentManager().removeContentManagerListener(myPostponedEventsListener);

      for (Content content : myToolWindow.getContentManager().getContents()) {
        if (content instanceof TabbedContent) {
          content.removePropertyChangeListener(myPostponedEventsListener);
        }
      }
    }
  }

  @NotNull
  public Set<String> getTabNames() {
    return StreamEx.of(myRefresher.getLogWindows())
      .select(VcsLogTab.class)
      .map(VcsLogTab::getTabName)
      .toSet();
  }

  public class VcsLogTab extends PostponableLogRefresher.VcsLogWindow {
    @NotNull private final String myTabName;

    public VcsLogTab(@NotNull VisiblePackRefresher refresher, @NotNull String tabName) {
      super(refresher);
      myTabName = tabName;
    }

    @Override
    public boolean isVisible() {
      String selectedTab = getSelectedTabName();
      return selectedTab != null && myTabName.equals(selectedTab);
    }

    @NotNull
    public String getTabName() {
      return myTabName;
    }
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
      VcsLogWindow logWindow = ContainerUtil.find(myRefresher.getLogWindows(),
                                                  window -> window instanceof VcsLogTab && ((VcsLogTab)window).myTabName.equals(tabName));
      if (logWindow != null) {
        myRefresher.filtererActivated(logWindow.getRefresher(), false);
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
      if (myToolWindow == null) return;
      if (myToolWindowManager.getToolWindow(TOOLWINDOW_ID) == null) {
        removeListeners();
      } else {
        selectionChanged();
      }
    }

    @Override
    public void toolWindowRegistered(@NotNull String id) {
      if (id.equals(TOOLWINDOW_ID)) {
        installContentListener();
      }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      if (evt.getPropertyName().equals(Content.PROP_COMPONENT)) {
        selectionChanged();
      }
    }
  }
}

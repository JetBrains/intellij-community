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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
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
import com.intellij.vcs.log.data.VcsLogFilterer;
import com.intellij.vcs.log.impl.PostponableLogRefresher.VcsLogWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class VcsLogTabsWatcher implements Disposable {
  private static final String TOOLWINDOW_ID = ChangesViewContentManager.TOOLWINDOW_ID;

  @NotNull private final PostponableLogRefresher myRefresher;

  @NotNull private final ToolWindowManagerImpl myToolWindowManager;
  @NotNull private final ToolWindowImpl myToolWindow;
  @NotNull private final MyRefreshPostponedEventsListener myPostponedEventsListener;

  public VcsLogTabsWatcher(@NotNull Project project, @NotNull PostponableLogRefresher refresher, @NotNull Disposable parentDisposable) {
    myRefresher = refresher;
    myToolWindowManager = (ToolWindowManagerImpl)ToolWindowManager.getInstance(project);
    myToolWindow = ObjectUtils.assertNotNull((ToolWindowImpl)myToolWindowManager.getToolWindow(TOOLWINDOW_ID));

    myPostponedEventsListener = new MyRefreshPostponedEventsListener();
    myToolWindow.getContentManager().addContentManagerListener(myPostponedEventsListener);
    myToolWindowManager.addToolWindowManagerListener(myPostponedEventsListener);

    Disposer.register(parentDisposable, this);
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

  @NotNull
  public Disposable addTabToWatch(@NotNull String contentTabName, @NotNull VcsLogFilterer filterer) {
    return myRefresher.addLogWindow(new VcsLogTab(filterer, contentTabName));
  }

  @Override
  public void dispose() {
    myToolWindow.getContentManager().removeContentManagerListener(myPostponedEventsListener);
    myToolWindowManager.removeToolWindowManagerListener(myPostponedEventsListener);

    for (Content content : myToolWindow.getContentManager().getContents()) {
      if (content instanceof TabbedContent) {
        content.removePropertyChangeListener(myPostponedEventsListener);
      }
    }
  }

  public class VcsLogTab extends PostponableLogRefresher.VcsLogWindow {
    @NotNull private final String myTabName;

    public VcsLogTab(@NotNull VcsLogFilterer filterer, @NotNull String tabName) {
      super(filterer);
      myTabName = tabName;
    }

    @Override
    public boolean isVisible() {
      String selectedTab = getSelectedTabName();
      return selectedTab != null && myTabName.equals(selectedTab);
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
      VcsLogWindow logWindow = ContainerUtil.find(myRefresher.getLogWindows(), new Condition<VcsLogWindow>() {
        @Override
        public boolean value(VcsLogWindow window) {
          return window instanceof VcsLogTab && ((VcsLogTab)window).myTabName.equals(tabName);
        }
      });
      if (logWindow != null) {
        myRefresher.filtererActivated(logWindow.getFilterer(), false);
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

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.impl.PostponableLogRefresher.VcsLogWindow;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class VcsLogTabsWatcher implements Disposable {
  private static final String TOOLWINDOW_ID = ChangesViewContentManager.TOOLWINDOW_ID;
  private static final Logger LOG = Logger.getInstance(VcsLogTabsWatcher.class);

  @NotNull private final PostponableLogRefresher myRefresher;

  @NotNull private final ToolWindowManagerEx myToolWindowManager;
  @NotNull private final MyRefreshPostponedEventsListener myPostponedEventsListener;
  @Nullable private ToolWindow myToolWindow;
  private boolean myIsVisible;
  @NotNull private MessageBusConnection myConnection;

  public VcsLogTabsWatcher(@NotNull Project project, @NotNull PostponableLogRefresher refresher) {
    myRefresher = refresher;
    myToolWindowManager = ToolWindowManagerEx.getInstanceEx(project);

    myPostponedEventsListener = new MyRefreshPostponedEventsListener();
    ApplicationManager.getApplication().invokeLater(() -> {
      myConnection = project.getMessageBus().connect();
      myConnection.subscribe(ToolWindowManagerListener.TOPIC, myPostponedEventsListener);
      installContentListener();
    }, project.getDisposed());
  }

  @Nullable
  private String getSelectedTabId() {
    if (myToolWindow != null && myToolWindow.isVisible()) {
      Content content = myToolWindow.getContentManager().getSelectedContent();
      if (content != null) {
        return VcsLogContentUtil.getId(content);
      }
    }
    return null;
  }

  @NotNull
  public Disposable addTabToWatch(@NotNull String tabId, @NotNull VisiblePackRefresher refresher) {
    return myRefresher.addLogWindow(new VcsLogTab(refresher, tabId));
  }

  private void installContentListener() {
    ToolWindow window = myToolWindowManager.getToolWindow(TOOLWINDOW_ID);
    if (window != null) {
      myToolWindow = window;
      myIsVisible = myToolWindow.isVisible();
      myToolWindow.getContentManager().addContentManagerListener(myPostponedEventsListener);
    }
  }

  private void removeListeners() {
    myConnection.disconnect();

    if (myToolWindow != null) {
      myToolWindow.getContentManager().removeContentManagerListener(myPostponedEventsListener);

      for (Content content : myToolWindow.getContentManager().getContents()) {
        if (content instanceof TabbedContent) {
          content.removePropertyChangeListener(myPostponedEventsListener);
        }
      }
    }
  }

  public void closeLogTabs() {
    if (myToolWindow != null) {
      Collection<String> tabs = getTabs();
      for (String tabId : tabs) {
        boolean closed = VcsLogContentUtil.closeLogTab(myToolWindow.getContentManager(), tabId);
        LOG.assertTrue(closed, "Could not find content component for tab " + tabId + "\nExisting content: " +
                               Arrays.toString(myToolWindow.getContentManager().getContents()) + "\nTabs to close: " + tabs);
      }
    }
  }

  @NotNull
  private List<String> getTabs() {
    return StreamEx.of(myRefresher.getLogWindows())
                   .select(VcsLogTab.class)
                   .map(VcsLogTab::getTabId)
                   .filter(tabId -> !VcsLogTabsProperties.MAIN_LOG_ID.equals(tabId))
                   .toList();
  }

  @Override
  public void dispose() {
    removeListeners();
  }

  public class VcsLogTab extends PostponableLogRefresher.VcsLogWindow {
    @NotNull private final String myTabId;

    public VcsLogTab(@NotNull VisiblePackRefresher refresher, @NotNull String tabId) {
      super(refresher);
      myTabId = tabId;
    }

    @Override
    public boolean isVisible() {
      String selectedTab = getSelectedTabId();
      return selectedTab != null && myTabId.equals(selectedTab);
    }

    @NotNull
    public String getTabId() {
      return myTabId;
    }
  }

  private class MyRefreshPostponedEventsListener extends ContentManagerAdapter
    implements ToolWindowManagerListener, PropertyChangeListener {

    private void selectionChanged() {
      String tabId = getSelectedTabId();
      if (tabId != null) {
        selectionChanged(tabId);
      }
    }

    private void selectionChanged(@NotNull String tabId) {
      VcsLogWindow logWindow = ContainerUtil.find(myRefresher.getLogWindows(),
                                                  window -> window instanceof VcsLogTab && ((VcsLogTab)window).myTabId.equals(tabId));
      if (logWindow != null) {
        myRefresher.refresherActivated(logWindow.getRefresher(), false);
      }
    }

    @Override
    public void selectionChanged(ContentManagerEvent event) {
      if (ContentManagerEvent.ContentOperation.add.equals(event.getOperation())) {
        String tabId = VcsLogContentUtil.getId(event.getContent());
        if (tabId != null) {
          selectionChanged(tabId);
        }
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
      }
      else if (myIsVisible != myToolWindow.isVisible()) {
        myIsVisible = myToolWindow.isVisible();
        selectionChanged();
      }
    }

    @Override
    public void toolWindowRegistered(@NotNull String toolWindowId) {
      if (toolWindowId.equals(TOOLWINDOW_ID)) {
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

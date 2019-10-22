// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.impl.PostponableLogRefresher.VcsLogWindow;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
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

  @NotNull private final Project myProject;
  @NotNull private final PostponableLogRefresher myRefresher;
  @NotNull private final ToolWindowManagerEx myToolWindowManager;

  @NotNull private final Disposable myListenersDisposable = Disposer.newDisposable();

  public VcsLogTabsWatcher(@NotNull Project project, @NotNull PostponableLogRefresher refresher) {
    myProject = project;
    myRefresher = refresher;
    myToolWindowManager = ToolWindowManagerEx.getInstanceEx(project);

    project.getMessageBus().connect(this).subscribe(ToolWindowManagerListener.TOPIC, new MyToolWindowManagerListener());
    installContentListeners();
  }

  @NotNull
  public Disposable addTabToWatch(@NotNull String tabId, @NotNull VisiblePackRefresher refresher, boolean isClosedOnDispose) {
    return myRefresher.addLogWindow(new VcsLogTab(refresher, tabId, isClosedOnDispose));
  }

  private void installContentListeners() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ToolWindow toolWindow = getToolWindow();
    if (toolWindow != null) {
      addContentManagerListener(toolWindow, new MyRefreshPostponedEventsListener(toolWindow), myListenersDisposable);
    }
  }

  private void removeContentListeners() {
    Disposer.dispose(myListenersDisposable);
  }

  private void closeLogTabs() {
    ToolWindow window = getToolWindow();
    if (window != null) {
      Collection<String> tabs = getTabsToClose();
      for (String tabId : tabs) {
        boolean closed = VcsLogContentUtil.closeLogTab(window.getContentManager(), tabId);
        LOG.assertTrue(closed, "Could not find content component for tab " + tabId + "\nExisting content: " +
                               Arrays.toString(window.getContentManager().getContents()) + "\nTabs to close: " + tabs);
      }
    }
  }

  @NotNull
  private List<String> getTabsToClose() {
    return StreamEx.of(myRefresher.getLogWindows())
      .select(VcsLogTab.class)
      .filter(VcsLogTab::isClosedOnDispose)
      .map(VcsLogTab::getTabId)
      .toList();
  }

  @Nullable
  private ToolWindow getToolWindow() {
    return myToolWindowManager.getToolWindow(TOOLWINDOW_ID);
  }

  @Override
  public void dispose() {
    closeLogTabs();
    removeContentListeners();
  }

  @Nullable
  private static String getSelectedTabId(@Nullable ToolWindow toolWindow) {
    if (toolWindow == null || !toolWindow.isVisible()) {
      return null;
    }

    Content content = toolWindow.getContentManager().getSelectedContent();
    if (content != null) {
      return VcsLogContentUtil.getId(content);
    }
    return null;
  }

  private static void addContentManagerListener(@NotNull ToolWindow window, @NotNull ContentManagerListener listener,
                                                @NotNull Disposable disposable) {
    window.getContentManager().addContentManagerListener(listener);
    Disposer.register(disposable, () -> {
      if (!window.isDisposed()) {
        window.getContentManager().removeContentManagerListener(listener);
      }
    });
  }

  private class VcsLogTab extends VcsLogWindow {
    @NotNull private final String myTabId;
    private final boolean myIsClosedOnDispose;

    private VcsLogTab(@NotNull VisiblePackRefresher refresher, @NotNull String tabId, boolean isClosedOnDispose) {
      super(refresher);
      myTabId = tabId;
      myIsClosedOnDispose = isClosedOnDispose;
    }

    @Override
    public boolean isVisible() {
      String selectedTab = getSelectedTabId(getToolWindow());
      return selectedTab != null && myTabId.equals(selectedTab);
    }

    @NotNull
    public String getTabId() {
      return myTabId;
    }

    public boolean isClosedOnDispose() {
      return myIsClosedOnDispose;
    }

    @Override
    public String toString() {
      return "VcsLogTab '" + myTabId + '\'';
    }
  }

  private class MyToolWindowManagerListener implements ToolWindowManagerListener {
    @Override
    public void toolWindowRegistered(@NotNull String id) {
      if (id.equals(TOOLWINDOW_ID)) {
        installContentListeners();
      }
    }

    @Override
    public void toolWindowUnregistered(@NotNull String id, @NotNull ToolWindow toolWindow) {
      if (id.equals(TOOLWINDOW_ID)) {
        removeContentListeners();
      }
    }
  }

  private class MyRefreshPostponedEventsListener extends VcsLogTabsListener {
    private MyRefreshPostponedEventsListener(@NotNull ToolWindow toolWindow) {
      super(myProject, toolWindow, myListenersDisposable);
    }

    @Override
    protected void selectionChanged(@NotNull String tabId) {
      VcsLogWindow logWindow = ContainerUtil.find(myRefresher.getLogWindows(),
                                                  window -> window instanceof VcsLogTab && ((VcsLogTab)window).myTabId.equals(tabId));
      if (logWindow != null) {
        LOG.debug("Selected log window '" + logWindow + "'");
        VcsLogUsageTriggerCollector.triggerUsage(VcsLogUsageTriggerCollector.VcsLogEvent.TAB_NAVIGATED, null);
        myRefresher.refresherActivated(logWindow.getRefresher(), false);
      }
    }
  }

  private static abstract class VcsLogTabsListener extends ContentManagerAdapter
    implements ToolWindowManagerListener, PropertyChangeListener {
    @NotNull private final ToolWindow myToolWindow;
    private boolean myIsVisible;

    private VcsLogTabsListener(@NotNull Project project, @NotNull ToolWindow toolWindow, @NotNull Disposable disposable) {
      myToolWindow = toolWindow;
      myIsVisible = toolWindow.isVisible();

      project.getMessageBus().connect(disposable).subscribe(ToolWindowManagerListener.TOPIC, this);
      Disposer.register(disposable, () -> {
        for (Content content : myToolWindow.getContentManager().getContents()) {
          if (content instanceof TabbedContent) {
            content.removePropertyChangeListener(this);
          }
        }
      });
    }

    protected abstract void selectionChanged(@NotNull String tabId);

    private void selectionChanged() {
      String tabId = getSelectedTabId(myToolWindow);
      if (tabId != null) {
        selectionChanged(tabId);
      }
    }

    @Override
    public void selectionChanged(@NotNull ContentManagerEvent event) {
      if (ContentManagerEvent.ContentOperation.add.equals(event.getOperation())) {
        String tabId = VcsLogContentUtil.getId(event.getContent());
        if (tabId != null) {
          selectionChanged(tabId);
        }
      }
    }

    @Override
    public void contentAdded(@NotNull ContentManagerEvent event) {
      Content content = event.getContent();
      if (content instanceof TabbedContent) {
        content.addPropertyChangeListener(this);
      }
    }

    @Override
    public void contentRemoved(@NotNull ContentManagerEvent event) {
      Content content = event.getContent();
      if (content instanceof TabbedContent) {
        content.removePropertyChangeListener(this);
      }
    }

    @Override
    public void stateChanged() {
      if (myIsVisible != myToolWindow.isVisible()) {
        myIsVisible = myToolWindow.isVisible();
        selectionChanged();
      }
    }

    @Override
    public void propertyChange(@NotNull PropertyChangeEvent evt) {
      if (evt.getPropertyName().equals(Content.PROP_COMPONENT)) {
        selectionChanged();
      }
    }
  }
}

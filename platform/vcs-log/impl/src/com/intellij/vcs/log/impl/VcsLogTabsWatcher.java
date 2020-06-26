// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.impl.PostponableLogRefresher.VcsLogWindow;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class VcsLogTabsWatcher implements Disposable {
  private static final Logger LOG = Logger.getInstance(VcsLogTabsWatcher.class);

  private final @NotNull Project myProject;
  private final @NotNull PostponableLogRefresher myRefresher;

  private final @NotNull Disposable myListenersDisposable = Disposer.newDisposable();

  public VcsLogTabsWatcher(@NotNull Project project, @NotNull PostponableLogRefresher refresher) {
    myProject = project;
    myRefresher = refresher;

    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(ToolWindowManagerListener.TOPIC, new MyToolWindowManagerListener());
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileManagerListener());

    installContentListeners();
  }

  public @NotNull Disposable addTabToWatch(@NotNull String logId, @NotNull VisiblePackRefresher refresher,
                                           @NotNull VcsLogManager.LogWindowKind kind, boolean isClosedOnDispose) {
    VcsLogWindow window;
    switch (kind) {
      case TOOL_WINDOW:
        window = new VcsLogToolWindowTab(logId, refresher, isClosedOnDispose);
        break;
      case EDITOR:
        window = new VcsLogEditorTab(logId, refresher, isClosedOnDispose);
        break;
      default:
        window = new VcsLogWindow(logId, refresher);
    }
    return myRefresher.addLogWindow(window);
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

  private void selectionChanged(@NotNull String tabId) {
    VcsLogWindow logWindow = ContainerUtil.find(myRefresher.getLogWindows(), window -> window.getId().equals(tabId));
    if (logWindow != null) {
      LOG.debug("Selected log window '" + logWindow + "'");
      VcsLogUsageTriggerCollector.triggerUsage(VcsLogUsageTriggerCollector.VcsLogEvent.TAB_NAVIGATED, null);
      myRefresher.refresherActivated(logWindow.getRefresher(), false);
    }
  }

  private void closeLogTabs() {
    ToolWindow window = getToolWindow();
    if (window != null) {
      List<String> toolWindowTabs = getToolWindowTabsToClose();
      for (String tabId : toolWindowTabs) {
        boolean closed = VcsLogContentUtil.closeLogTab(window.getContentManager(), tabId);
        LOG.assertTrue(closed, "Could not find content component for tab " + tabId + "\nExisting content: " +
                               Arrays.toString(window.getContentManager().getContents()) + "\nTabs to close: " + toolWindowTabs);
      }
    }

    List<String> editorTabs = getEditorTabsToClose();
    boolean closed = VcsLogEditorUtilKt.closeLogTabs(myProject, editorTabs);
    LOG.assertTrue(closed, "Could not close tabs: " + editorTabs);
  }

  private @NotNull List<String> getToolWindowTabsToClose() {
    return StreamEx.of(myRefresher.getLogWindows())
      .select(VcsLogToolWindowTab.class)
      .filter(VcsLogToolWindowTab::isClosedOnDispose)
      .map(VcsLogWindow::getId)
      .toList();
  }

  private @NotNull List<String> getEditorTabsToClose() {
    return StreamEx.of(myRefresher.getLogWindows())
      .select(VcsLogEditorTab.class)
      .filter(VcsLogEditorTab::isClosedOnDispose)
      .map(VcsLogWindow::getId)
      .toList();
  }

  private @Nullable ToolWindow getToolWindow() {
    return ToolWindowManager.getInstance(myProject).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
  }

  @Override
  public void dispose() {
    closeLogTabs();
    removeContentListeners();
  }

  private static @Nullable String getSelectedToolWindowTabId(@Nullable ToolWindow toolWindow) {
    if (toolWindow == null || !toolWindow.isVisible()) {
      return null;
    }

    Content content = toolWindow.getContentManager().getSelectedContent();
    if (content != null) {
      return VcsLogContentUtil.getId(content);
    }
    return null;
  }

  private static @NotNull Set<String> getSelectedEditorTabIds(@NotNull Project project) {
    return VcsLogEditorUtilKt.findSelectedLogIds(project);
  }

  private static void addContentManagerListener(@NotNull ToolWindow window,
                                                @NotNull ContentManagerListener listener,
                                                @NotNull Disposable disposable) {
    window.getContentManager().addContentManagerListener(listener);
    Disposer.register(disposable, () -> {
      if (!window.isDisposed()) {
        window.getContentManager().removeContentManagerListener(listener);
      }
    });
  }

  private final class VcsLogToolWindowTab extends VcsLogWindow {
    private final boolean myIsClosedOnDispose;

    private VcsLogToolWindowTab(@NotNull String id, @NotNull VisiblePackRefresher refresher, boolean isClosedOnDispose) {
      super(id, refresher);
      myIsClosedOnDispose = isClosedOnDispose;
    }

    @Override
    public boolean isVisible() {
      String selectedTab = getSelectedToolWindowTabId(getToolWindow());
      return getId().equals(selectedTab);
    }

    public boolean isClosedOnDispose() {
      return myIsClosedOnDispose;
    }
  }

  private final class VcsLogEditorTab extends VcsLogWindow {
    private final boolean myIsClosedOnDispose;

    private VcsLogEditorTab(@NotNull String id, @NotNull VisiblePackRefresher refresher, boolean isClosedOnDispose) {
      super(id, refresher);
      myIsClosedOnDispose = isClosedOnDispose;
    }

    @Override
    public boolean isVisible() {
      return getSelectedEditorTabIds(myProject).contains(getId());
    }

    public boolean isClosedOnDispose() {
      return myIsClosedOnDispose;
    }
  }

  private final class MyToolWindowManagerListener implements ToolWindowManagerListener {
    @Override
    public void toolWindowsRegistered(@NotNull List<String> ids) {
      if (ids.contains(ChangesViewContentManager.TOOLWINDOW_ID)) {
        installContentListeners();
      }
    }

    @Override
    public void toolWindowUnregistered(@NotNull String id, @NotNull ToolWindow toolWindow) {
      if (id.equals(ChangesViewContentManager.TOOLWINDOW_ID)) {
        removeContentListeners();
      }
    }
  }

  private class MyFileManagerListener implements FileEditorManagerListener {
    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent e) {
      FileEditor editor = e.getNewEditor();
      if (editor != null) {
        for (String tabId : VcsLogEditorUtilKt.getLogIds(editor)) {
          VcsLogTabsWatcher.this.selectionChanged(tabId);
        }
      }
    }
  }

  private final class MyRefreshPostponedEventsListener extends VcsLogTabsListener {
    private MyRefreshPostponedEventsListener(@NotNull ToolWindow toolWindow) {
      super(myProject, toolWindow, myListenersDisposable);
    }

    @Override
    protected void selectionChanged(@NotNull String tabId) {
      VcsLogTabsWatcher.this.selectionChanged(tabId);
    }
  }

  private abstract static class VcsLogTabsListener
    implements ToolWindowManagerListener, PropertyChangeListener, ContentManagerListener {
    private final @NotNull ToolWindow myToolWindow;

    private VcsLogTabsListener(@NotNull Project project, @NotNull ToolWindow toolWindow, @NotNull Disposable disposable) {
      myToolWindow = toolWindow;

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
      String tabId = getSelectedToolWindowTabId(myToolWindow);
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
    public void toolWindowShown(@NotNull ToolWindow toolWindow) {
      if (myToolWindow == toolWindow) selectionChanged();
    }

    @Override
    public void propertyChange(@NotNull PropertyChangeEvent evt) {
      if (evt.getPropertyName().equals(Content.PROP_COMPONENT)) {
        selectionChanged();
      }
    }
  }
}

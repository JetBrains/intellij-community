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
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.content.TabbedContent;
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
import java.util.List;
import java.util.Set;

public final class VcsLogTabsWatcher implements Disposable {
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
    project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileManagerListener());

    installContentListeners();
  }

  @NotNull
  public Disposable addTabToWatch(@NotNull String logId, @NotNull VisiblePackRefresher refresher,
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
    for (String tabId : editorTabs) {
      boolean closed = VcsLogEditorUtilKt.closeLogTab(myProject, tabId);
      LOG.assertTrue(closed, "Could not find editor for tab " + tabId + "\nTabs to close: " + editorTabs);
    }
  }

  @NotNull
  private List<String> getToolWindowTabsToClose() {
    return StreamEx.of(myRefresher.getLogWindows())
      .select(VcsLogToolWindowTab.class)
      .filter(VcsLogToolWindowTab::isClosedOnDispose)
      .map(VcsLogWindow::getId)
      .toList();
  }

  @NotNull
  private List<String> getEditorTabsToClose() {
    return StreamEx.of(myRefresher.getLogWindows())
      .select(VcsLogEditorTab.class)
      .filter(VcsLogEditorTab::isClosedOnDispose)
      .map(VcsLogWindow::getId)
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
  private static String getSelectedToolWindowTabId(@Nullable ToolWindow toolWindow) {
    if (toolWindow == null || !toolWindow.isVisible()) {
      return null;
    }

    Content content = toolWindow.getContentManager().getSelectedContent();
    if (content != null) {
      return VcsLogContentUtil.getId(content);
    }
    return null;
  }

  @NotNull
  private static Set<String> getSelectedEditorTabIds(@NotNull Project project) {
    return VcsLogEditorUtilKt.findSelectedLogIds(project);
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

  private class VcsLogToolWindowTab extends VcsLogWindow {
    private final boolean myIsClosedOnDispose;

    private VcsLogToolWindowTab(@NotNull String id, @NotNull VisiblePackRefresher refresher, boolean isClosedOnDispose) {
      super(id, refresher);
      myIsClosedOnDispose = isClosedOnDispose;
    }

    @Override
    public boolean isVisible() {
      String selectedTab = getSelectedToolWindowTabId(getToolWindow());
      return selectedTab != null && getId().equals(selectedTab);
    }

    public boolean isClosedOnDispose() {
      return myIsClosedOnDispose;
    }
  }

  private class VcsLogEditorTab extends VcsLogWindow {
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

  private class MyFileManagerListener implements FileEditorManagerListener {
    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent e) {
      FileEditor editor = e.getNewEditor();
      if (editor != null) {
        String tabId = VcsLogEditorUtilKt.getLogId(editor);
        if (tabId != null) {
          VcsLogTabsWatcher.this.selectionChanged(tabId);
        }
      }
    }
  }

  private class MyRefreshPostponedEventsListener extends VcsLogTabsListener {
    private MyRefreshPostponedEventsListener(@NotNull ToolWindow toolWindow) {
      super(myProject, toolWindow, myListenersDisposable);
    }

    @Override
    protected void selectionChanged(@NotNull String tabId) {
      VcsLogTabsWatcher.this.selectionChanged(tabId);
    }
  }

  private static abstract class VcsLogTabsListener
    implements ToolWindowManagerListener, PropertyChangeListener, ContentManagerListener {
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
    public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
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

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.diff.editor.GraphViewVirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.impl.PostponableLogRefresher.VcsLogWindow;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogPanel;
import com.intellij.vcs.log.ui.frame.MainFrame;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
  @NotNull private final MyRefreshPostponedEventsListener myPostponedEventsListener;
  @NotNull private final MessageBusConnection myConnection;
  @NotNull private final MyLogEditorListener myLogEditorListener;
  @Nullable private ToolWindow myToolWindow;
  private boolean myIsVisible;

  public VcsLogTabsWatcher(@NotNull Project project, @NotNull PostponableLogRefresher refresher) {
    myProject = project;
    myRefresher = refresher;
    myToolWindowManager = ToolWindowManagerEx.getInstanceEx(project);

    myPostponedEventsListener = new MyRefreshPostponedEventsListener();
    myLogEditorListener = new MyLogEditorListener();
    myConnection = project.getMessageBus().connect();
    myConnection.subscribe(ToolWindowManagerListener.TOPIC, myPostponedEventsListener);

    installContentListener();
    installLogEditorListeners();
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
    ApplicationManager.getApplication().assertIsDispatchThread();
    ToolWindow window = myToolWindowManager.getToolWindow(TOOLWINDOW_ID);
    if (window != null) {
      myToolWindow = window;
      myIsVisible = myToolWindow.isVisible();
      myToolWindow.getContentManager().addContentManagerListener(myPostponedEventsListener);
    }
  }


  private void processVirtualFile(VirtualFile file) {
    if (file instanceof GraphViewVirtualFile) {
      ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);

      if (window != null) {
        for (Content content : window.getContentManager().getContents()) {
          JComponent component = content.getComponent();
          String logId = file.getUserData(GraphViewVirtualFile.TabContentId);

          if (component instanceof VcsLogPanel) {
            AbstractVcsLogUi ui = ((VcsLogPanel)component).getUi();
            if (ui.getId().equals(logId)) {
              content.putUserData(GraphViewVirtualFile.GraphVirtualFile, (GraphViewVirtualFile)file);
            }
          }
          else if (VcsLogContentProvider.TAB_NAME.equals(content.getDisplayName()) &&
                   VcsLogProjectTabsProperties.MAIN_LOG_ID.equals(logId)) {
            content.putUserData(GraphViewVirtualFile.GraphVirtualFile, (GraphViewVirtualFile)file);
          }
        }
      }
    }
  }

  private void installLogEditorListeners() {
    if (!Registry.is("show.log.as.editor.tab")) {
      return;
    }

    ToolWindow toolWindow = myToolWindowManager.getToolWindow(TOOLWINDOW_ID);
    if (toolWindow != null) {
      toolWindow.getContentManager().addContentManagerListener(myLogEditorListener);

      myProject.getMessageBus().connect(this)
        .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
          @Override
          public void selectionChanged(@NotNull FileEditorManagerEvent e) {
            VirtualFile file = e.getNewFile();
            if (file instanceof GraphViewVirtualFile) {
              String data = file.getUserData(GraphViewVirtualFile.TabContentId);
              if (data != null) {
                VcsLogContentUtil.findAndSelect(myProject, AbstractVcsLogUi.class, ui -> {
                  return ui.getId() == data;
                });
              }
            }
          }

          @Override
          public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
            processVirtualFile(file);
          }
        });
    }
  }

  private void removeListeners() {
    myConnection.disconnect();

    if (myToolWindow != null) {
      myToolWindow.getContentManager().removeContentManagerListener(myPostponedEventsListener);
      myToolWindow.getContentManager().removeContentManagerListener(myLogEditorListener);

      for (Content content : myToolWindow.getContentManager().getContents()) {
        if (content instanceof TabbedContent) {
          content.removePropertyChangeListener(myPostponedEventsListener);
        }
      }
    }
  }

  private void closeLogTabs() {
    ToolWindow window = (myToolWindow != null) ? myToolWindow : myToolWindowManager.getToolWindow(TOOLWINDOW_ID);
    if (window != null) {
      Collection<String> tabs = getTabs();
      for (String tabId : tabs) {
        boolean closed = VcsLogContentUtil.closeLogTab(window.getContentManager(), tabId);
        LOG.assertTrue(closed, "Could not find content component for tab " + tabId + "\nExisting content: " +
                               Arrays.toString(window.getContentManager().getContents()) + "\nTabs to close: " + tabs);
      }
    }
  }

  @NotNull
  private List<String> getTabs() {
    return StreamEx.of(myRefresher.getLogWindows())
      .select(VcsLogTab.class)
      .map(VcsLogTab::getTabId)
      .filter(tabId -> !VcsLogProjectTabsProperties.MAIN_LOG_ID.equals(tabId))
      .toList();
  }

  @Override
  public void dispose() {
    closeLogTabs();
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

    @Override
    public String toString() {
      return "VcsLogTab '" + myTabId + '\'';
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
        LOG.debug("Selected log window '" + logWindow + "'");
        VcsLogUsageTriggerCollector.triggerUsage(VcsLogUsageTriggerCollector.VcsLogEvent.TAB_NAVIGATED, null);
        myRefresher.refresherActivated(logWindow.getRefresher(), false);
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

  private class MyLogEditorListener extends ContentManagerAdapter implements PropertyChangeListener {

    @Override
    public void selectionChanged(@NotNull ContentManagerEvent event) {
      if (ContentManagerEvent.ContentOperation.add.equals(event.getOperation())) {
        Content content = event.getContent();
        selectEditorTab(content);
      }
    }

    private void selectEditorTab(Content content) {
      GraphViewVirtualFile file = content.getUserData(GraphViewVirtualFile.GraphVirtualFile);
      if (file != null) {
        MainFrame.openLogEditorTab(file, myProject);
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
    public void propertyChange(PropertyChangeEvent evt) {
      if (evt.getPropertyName().equals(Content.PROP_COMPONENT)) {

        if (myToolWindow != null && myToolWindow.isVisible()) {
          Content content = myToolWindow.getContentManager().getSelectedContent();
          if (content != null) {
            selectEditorTab(content);
          }
        }
      }
    }
  }
}

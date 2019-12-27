// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.wm.*;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.icons.AllIcons.Ide.IncomingChangesOn;
import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.util.ObjectUtils.notNull;

public class IncomingChangesIndicator implements StatusBarWidget, StatusBarWidget.IconPresentation {
  private static final Logger LOG = Logger.getInstance(IncomingChangesIndicator.class);

  private static final Icon INCOMING_ICON = IncomingChangesOn;
  private static final Icon DISABLED_INCOMING_ICON = notNull(IconLoader.getDisabledIcon(IncomingChangesOn), IncomingChangesOn);

  @NotNull private final Project myProject;
  private StatusBar myStatusBar;
  private int myIncomingChangesCount;
  private boolean myIsIncomingChangesAvailable;

  public IncomingChangesIndicator(@NotNull Project project) {
    myProject = project;
  }

  private void setIncomingChangesCount(int incomingChangesCount) {
    LOG.debug("Refreshing indicator: " + incomingChangesCount + " changes");

    myIncomingChangesCount = incomingChangesCount;
    if (myStatusBar != null) myStatusBar.updateWidget(ID());
  }

  @Override
  @NotNull
  public String ID() {
    return "IncomingChanges";
  }

  @Override
  public WidgetPresentation getPresentation() {
    return this;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    if (!myIsIncomingChangesAvailable) return null; // hide widget

    return myIncomingChangesCount > 0 ? INCOMING_ICON : DISABLED_INCOMING_ICON;
  }

  @Nullable
  @Override
  public String getTooltipText() {
    if (!myIsIncomingChangesAvailable) return null;

    return myIncomingChangesCount > 0
           ? message("incoming.changes.indicator.tooltip", myIncomingChangesCount)
           : "No incoming changelists available";
  }

  @Override
  public Consumer<MouseEvent> getClickConsumer() {
    return mouseEvent -> {
      if (myStatusBar != null) {
        DataContext dataContext = DataManager.getInstance().getDataContext((Component)myStatusBar);
        final Project project = CommonDataKeys.PROJECT.getData(dataContext);
        if (project != null) {
          ToolWindow changesView = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
          changesView.show(() -> ChangesViewContentManager.getInstance(project).selectContent("Incoming"));
        }
      }
    };
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;

    final MessageBusConnection connection = myProject.getMessageBus().connect(this);
    connection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new CommittedChangesListener() {
      @Override
      public void incomingChangesUpdated(@Nullable List<CommittedChangeList> receivedChanges) {
        getApplication().invokeLater(() -> refreshIndicator());
      }

      @Override
      public void changesCleared() {
        getApplication().invokeLater(() -> refreshIndicator());
      }
    });
    final VcsListener listener = new VcsListener() {
      @Override
      public void directoryMappingChanged() {
        if (myProject.isDisposed()) return;
        UIUtil.invokeLaterIfNeeded(() -> updateIndicatorVisibility());
      }
    };
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, listener);
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED_IN_PLUGIN, listener);
  }

  @Override
  public void dispose() {
    myStatusBar = null;
  }

  private void updateIndicatorVisibility() {
    if (myStatusBar == null) return;

    if (needIndicator()) {
      myIsIncomingChangesAvailable = true;
      refreshIndicator();
    }
    else {
      myIsIncomingChangesAvailable = false;
      setIncomingChangesCount(0);
    }
  }

  private boolean needIndicator() {
    final AbstractVcs[] vcss = ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss();
    for (AbstractVcs vcs : vcss) {
      CachingCommittedChangesProvider provider = vcs.getCachingCommittedChangesProvider();
      if (provider != null && provider.supportsIncomingChanges()) {
        return true;
      }
    }
    return false;
  }

  private void refreshIndicator() {
    if (myStatusBar == null) return;

    final List<CommittedChangeList> list = CommittedChangesCache.getInstance(myProject).getCachedIncomingChanges();
    setIncomingChangesCount(list == null || list.isEmpty() ? 0 : list.size());
  }

  public static class Provider implements StatusBarWidgetProvider {
    @NotNull
    @Override
    public StatusBarWidget getWidget(@NotNull Project project) {
      return new IncomingChangesIndicator(project);
    }
  }
}

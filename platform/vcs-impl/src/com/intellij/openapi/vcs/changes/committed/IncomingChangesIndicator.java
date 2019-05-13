// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.wm.*;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.icons.AllIcons.Ide.IncomingChangesOn;
import static com.intellij.util.ObjectUtils.notNull;

/**
 * @author yole
 */
public class IncomingChangesIndicator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.IncomingChangesIndicator");

  private final Project myProject;
  private final CommittedChangesCache myCache;
  private IndicatorComponent myIndicatorComponent;

  public IncomingChangesIndicator(Project project, CommittedChangesCache cache, MessageBus bus) {
    myProject = project;
    myCache = cache;
    final MessageBusConnection connection = bus.connect();
    connection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new CommittedChangesAdapter() {
      @Override
      public void incomingChangesUpdated(@Nullable final List<CommittedChangeList> receivedChanges) {
        ApplicationManager.getApplication().invokeLater(() -> refreshIndicator());
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

  private void updateIndicatorVisibility() {
    final StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (needIndicator()) {
      if (myIndicatorComponent == null) {
        myIndicatorComponent = new IndicatorComponent();
        statusBar.addWidget(myIndicatorComponent, myProject);
        refreshIndicator();
      }
    }
    else {
      if (myIndicatorComponent != null) {
        statusBar.removeWidget(myIndicatorComponent.ID());
        myIndicatorComponent = null;
      }
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
    if (myIndicatorComponent == null) {
      return;
    }
    final List<CommittedChangeList> list = myCache.getCachedIncomingChanges();
    if (list == null || list.isEmpty()) {
      debug("Refreshing indicator: no changes");
      myIndicatorComponent.clear();
    }
    else {
      debug("Refreshing indicator: " + list.size() + " changes");
      myIndicatorComponent.setChangesAvailable(VcsBundle.message("incoming.changes.indicator.tooltip", list.size()));
    }
  }

  private static void debug(@NonNls final String message) {
    LOG.debug(message);
  }

  private static class IndicatorComponent implements StatusBarWidget, StatusBarWidget.IconPresentation {

    private static final Icon INCOMING_ICON = IncomingChangesOn;
    private static final Icon DISABLED_INCOMING_ICON = notNull(IconLoader.getDisabledIcon(IncomingChangesOn), IncomingChangesOn);

    private StatusBar myStatusBar;

    private Icon myCurrentIcon = DISABLED_INCOMING_ICON;
    private String myToolTipText;

    private IndicatorComponent() {
    }

    void clear() {
      update(DISABLED_INCOMING_ICON, "No incoming changelists available");
    }

    void setChangesAvailable(@NotNull final String toolTipText) {
      update(INCOMING_ICON, toolTipText);
    }

    private void update(@NotNull final Icon icon, @Nullable final String toolTipText) {
      myCurrentIcon = icon;
      myToolTipText = toolTipText;
      if (myStatusBar != null) myStatusBar.updateWidget(ID());
    }

    @Override
    @NotNull
    public Icon getIcon() {
      return myCurrentIcon;
    }

    @Override
    public String getTooltipText() {
      return myToolTipText;
    }

    @Override
    public Consumer<MouseEvent> getClickConsumer() {
      return mouseEvent -> {
        if (myStatusBar != null) {
        DataContext dataContext = DataManager.getInstance().getDataContext((Component) myStatusBar);
        final Project project = CommonDataKeys.PROJECT.getData(dataContext);
        if (project != null) {
          ToolWindow changesView = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
          changesView.show(() -> ChangesViewContentManager.getInstance(project).selectContent("Incoming"));
        }
        }
      };
    }

    @Override
    @NotNull
    public String ID() {
      return "IncomingChanges";
    }

    @Override
    public WidgetPresentation getPresentation(@NotNull PlatformType type) {
      return this;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
      myStatusBar = statusBar;
    }

    @Override
    public void dispose() {
      myStatusBar = null;
    }
  }
}

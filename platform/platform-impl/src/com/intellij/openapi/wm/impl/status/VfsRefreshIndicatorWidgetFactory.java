// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.UIBundle;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class VfsRefreshIndicatorWidgetFactory implements StatusBarWidgetFactory {
  private static final String ID = "VFS_REFRESH";

  private volatile boolean myAvailable;

  public VfsRefreshIndicatorWidgetFactory() {
    // see also: `InfoAndProgressPanel#myShowNavBar`
    myAvailable = ExperimentalUI.isNewUI() && UISettings.getInstance().getShowNavigationBarInBottom();
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(UISettingsListener.TOPIC, newUiSettings -> {
      myAvailable = ExperimentalUI.isNewUI() && newUiSettings.getShowNavigationBarInBottom();
      var projectManager = ProjectManager.getInstanceIfCreated();
      if (projectManager != null) {
        for (var project : projectManager.getOpenProjects()) {
          project.getService(StatusBarWidgetsManager.class).updateWidget(this);
        }
      }
    });
  }

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Override
  public @NotNull String getDisplayName() {
    return UIBundle.message("status.bar.vfs.refresh.widget.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project) {
    return myAvailable;
  }

  @Override
  public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
    return true;
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
    return new VfsRefreshWidget();
  }

  @RequiresEdt
  public static void start(@NotNull StatusBar statusBar, @NotNull @NlsContexts.Tooltip String tooltipText) {
    var widget = ((VfsRefreshWidget)statusBar.getWidget(ID));
    if (widget != null) {
      widget.myComponent.setIcon(widget.myProgress);
      widget.myComponent.setEnabled(true);
      widget.myComponent.setToolTipText(tooltipText);
    }
  }

  @RequiresEdt
  public static void stop(@NotNull StatusBar statusBar) {
    var widget = ((VfsRefreshWidget)statusBar.getWidget(ID));
    if (widget != null) {
      widget.myComponent.setIcon(widget.myInactive);
      widget.myComponent.setEnabled(false);
      widget.myComponent.setToolTipText(UIBundle.message("status.bar.vfs.refresh.widget.tooltip"));
    }
  }

  private static final class VfsRefreshWidget implements CustomStatusBarWidget {
    private final Icon myInactive = EmptyIcon.ICON_16;
    private final Icon myProgress = new AnimatedIcon.FS();
    private final JLabel myComponent = new JLabel(myInactive);

    private VfsRefreshWidget() {
      myComponent.setEnabled(false);
    }

    @Override
    public @NotNull String ID() {
      return ID;
    }

    @Override
    public JComponent getComponent() {
      return myComponent;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) { }

    @Override
    public void dispose() { }
  }
}

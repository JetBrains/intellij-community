// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;

public final class DisablePCEAction extends DumbAwareToggleAction {
  private static final String STATUS_BAR_WIDGET_ID = "PCEDisabledStatus";

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return ProgressManager.getInstance().getClass() != ProgressManagerImpl.class;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    changePCEEnabledStatus(!state);
  }

  private static void changePCEEnabledStatus(boolean enabled) {
    ProgressManager newManager = createDebugManager(enabled);
    ((ComponentManagerImpl)ApplicationManager.getApplication()).replaceRegularServiceInstance(ProgressManager.class, newManager);
    CachedSingletonsRegistry.cleanupCachedFields();

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
      if (statusBar != null) {
        if (statusBar.getWidget(STATUS_BAR_WIDGET_ID) == null) {
          if (!enabled) {
            statusBar.addWidget(new StatusWidget(), "before " + StatusBar.StandardWidgets.POSITION_PANEL);
            statusBar.updateWidget(STATUS_BAR_WIDGET_ID);
          }
        }
        else {
          if (enabled) statusBar.removeWidget(STATUS_BAR_WIDGET_ID);
        }
      }
    }
  }

  @NotNull
  private static ProgressManager createDebugManager(boolean enabledPCE) {
    return enabledPCE ? new ProgressManagerImpl() : new ProgressManagerImpl(){
      @Override
      protected void doCheckCanceled() throws ProcessCanceledException {
        runCheckCanceledHooks(getProgressIndicator());
      }

      @Override
      public boolean isInNonCancelableSection() {
        return true;
      }
    };
  }

  private static final class StatusWidget implements StatusBarWidget, StatusBarWidget.TextPresentation {
    @NotNull
    @Override
    public String ID() {
      return STATUS_BAR_WIDGET_ID;
    }

    @Nullable
    @Override
    public WidgetPresentation getPresentation() {
      return this;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {}

    @Override
    public void dispose() {}

    @Nullable
    @Override
    public String getTooltipText() {
      return "Click to re-enable ProcessCanceledException";
    }

    @Nullable
    @Override
    public Consumer<MouseEvent> getClickConsumer() {
      return event -> changePCEEnabledStatus(true);
    }

    @NotNull
    @Override
    public String getText() {
      return "WARNING: PCE is disabled!";
    }

    @Override
    public float getAlignment() {
      return Component.CENTER_ALIGNMENT;
    }
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * "Power save mode: enabled/disabled" icon in the status bar
 */
final class PowerSaveStatusWidgetFactory implements StatusBarWidgetFactory {
  private static final String ID = "PowerSaveMode";

  PowerSaveStatusWidgetFactory() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(PowerSaveMode.TOPIC, () -> {
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
        if (statusBar != null) {
          statusBar.updateWidget(ID);
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
    return InspectionsBundle.message("power.save.mode.widget.display.name");
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
    return new PowerWidget();
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  private static final class PowerWidget implements StatusBarWidget, StatusBarWidget.IconPresentation {
    @Override
    public @NotNull String ID() {
      return ID;
    }

    @Override
    public @NotNull WidgetPresentation getPresentation() {
      return this;
    }

    @Override
    public @NotNull String getTooltipText() {
      return PowerSaveMode.isEnabled() ?
             InspectionsBundle.message("power.save.mode.widget.tooltip.enabled") :
             InspectionsBundle.message("power.save.mode.widget.tooltip.disabled");
    }

    @Override
    public @NotNull Consumer<MouseEvent> getClickConsumer() {
      return __ -> PowerSaveMode.setEnabled(!PowerSaveMode.isEnabled());
    }

    @Override
    public @NotNull Icon getIcon() {
      return PowerSaveMode.isEnabled() ? AllIcons.General.InspectionsPowerSaveMode : AllIcons.General.InspectionsEye;
    }
  }
}

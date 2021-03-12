// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * "Power save mode: enabled/disabled" icon in the status bar
 */
public class PowerSaveStatusWidgetFactory implements StatusBarWidgetFactory {
  private static final String ID = "PowerSaveMode";

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Nls
  @Override
  public @NotNull String getDisplayName() {
    return InspectionsBundle.message("power.save.mode.widget.display.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project) {
    return true;
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(PowerSaveMode.TOPIC,
      () -> WindowManager.getInstance().getStatusBar(project).updateWidget(getId()));
    return new PowerWidget();
  }

  @Override
  public void disposeWidget(@NotNull StatusBarWidget widget) {
  }

  @Override
  public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
    return true;
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  private static class PowerWidget implements StatusBarWidget, StatusBarWidget.IconPresentation {
    @Override
    public @NotNull String ID() {
      return ID;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
    }

    @Override
    public @Nullable WidgetPresentation getPresentation() {
      return this;
    }

    @Override
    public @Nullable String getTooltipText() {
      return PowerSaveMode.isEnabled() ?
             InspectionsBundle.message("power.save.mode.widget.tooltip.enabled") :
             InspectionsBundle.message("power.save.mode.widget.tooltip.disabled");
    }

    @Override
    public @Nullable Consumer<MouseEvent> getClickConsumer() {
      return __ -> PowerSaveMode.setEnabled(!PowerSaveMode.isEnabled());
    }

    @Override
    public @Nullable Icon getIcon() {
      return PowerSaveMode.isEnabled() ? AllIcons.General.InspectionsPowerSaveMode : AllIcons.General.InspectionsEye;
    }

    @Override
    public void dispose() {
    }
  }
}

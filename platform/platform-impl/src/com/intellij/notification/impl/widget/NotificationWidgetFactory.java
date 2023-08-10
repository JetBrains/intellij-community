// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl.widget;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;

final class NotificationWidgetFactory implements StatusBarWidgetFactory {
  public static boolean isAvailable() {
    return UISettings.getInstance().getHideToolStripes() || UISettings.getInstance().getPresentationMode();
  }

  @Override
  public @NotNull String getId() {
    return IdeNotificationArea.WIDGET_ID;
  }

  @Override
  public @NotNull String getDisplayName() {
    return UIBundle.message("status.bar.notifications.widget.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project) {
    return isAvailable();
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
    return new IdeNotificationArea();
  }

  @Override
  public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
    return isAvailable();
  }
}

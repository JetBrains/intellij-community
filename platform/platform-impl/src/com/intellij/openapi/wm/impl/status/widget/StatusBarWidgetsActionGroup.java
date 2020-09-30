// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status.widget;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.ui.UIBundle;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public class StatusBarWidgetsActionGroup extends ActionGroup {
  public static final String GROUP_ID = "ViewStatusBarWidgetsGroup";

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    Project project = e != null ? e.getProject() : null;
    if (project == null) return AnAction.EMPTY_ARRAY;

    StatusBarWidgetsManager manager = project.getService(StatusBarWidgetsManager.class);
    Collection<AnAction> toggleActions = new ArrayList<>(ContainerUtil.map(manager.getWidgetFactories(), ToggleWidgetAction::new));
    toggleActions.add(Separator.getInstance());
    toggleActions.add(new HideCurrentWidgetAction());
    return toggleActions.toArray(AnAction.EMPTY_ARRAY);
  }

  private static final class ToggleWidgetAction extends DumbAwareToggleAction {
    private final StatusBarWidgetFactory myWidgetFactory;

    private ToggleWidgetAction(@NotNull StatusBarWidgetFactory widgetFactory) {
      super(widgetFactory.getDisplayName());
      myWidgetFactory = widgetFactory;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      Project project = e.getProject();
      if (project == null) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }
      if (ActionPlaces.isMainMenuOrShortcut(e.getPlace())) {
        e.getPresentation().setEnabledAndVisible(myWidgetFactory.isConfigurable() && myWidgetFactory.isAvailable(project));
        return;
      }
      StatusBar statusBar = e.getData(PlatformDataKeys.STATUS_BAR);
      e.getPresentation().setEnabledAndVisible(statusBar != null && project.getService(StatusBarWidgetsManager.class)
        .canBeEnabledOnStatusBar(myWidgetFactory, statusBar));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return ServiceManager.getService(StatusBarWidgetSettings.class).isEnabled(myWidgetFactory);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      ServiceManager.getService(StatusBarWidgetSettings.class).setEnabled(myWidgetFactory, state);
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        project.getService(StatusBarWidgetsManager.class).updateWidget(myWidgetFactory);
      }
    }
  }

  private static class HideCurrentWidgetAction extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      StatusBarWidgetFactory factory = getFactory(e);
      if (factory == null) return;

      ServiceManager.getService(StatusBarWidgetSettings.class).setEnabled(factory, false);
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        project.getService(StatusBarWidgetsManager.class).updateWidget(factory);
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      StatusBarWidgetFactory factory = getFactory(e);
      e.getPresentation().setEnabledAndVisible(factory != null && factory.isConfigurable());
      if (factory != null) {
        e.getPresentation().setText(UIBundle.message("status.bar.hide.widget.action.name", factory.getDisplayName()));
      }
    }

    private @Nullable
    static StatusBarWidgetFactory getFactory(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      String hoveredWidgetId = e.getData(IdeStatusBarImpl.HOVERED_WIDGET_ID);
      StatusBar statusBar = e.getData(PlatformDataKeys.STATUS_BAR);
      if (project != null && hoveredWidgetId != null && statusBar != null) {
        return project.getService(StatusBarWidgetsManager.class).findWidgetFactory(hoveredWidgetId);
      }
      return null;
    }
  }
}

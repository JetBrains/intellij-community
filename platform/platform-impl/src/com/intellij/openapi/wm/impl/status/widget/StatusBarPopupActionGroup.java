// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status.widget;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.openapi.wm.impl.status.MemoryUsagePanel;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.ui.UIBundle;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class StatusBarPopupActionGroup extends ComputableActionGroup {
  @NotNull
  private final StatusBarWidgetsManager myManager;

  public StatusBarPopupActionGroup(@NotNull StatusBarWidgetsManager manager) {
    super(true);
    myManager = manager;
  }

  @NotNull
  @Override
  protected CachedValueProvider<AnAction[]> createChildrenProvider(@NotNull ActionManager actionManager) {
    return () -> {
      Collection<AnAction> toggleActions = ContainerUtil.map(myManager.getWidgetFactories(), ToggleWidgetAction::new);
      toggleActions.add(new MemoryIndicatorToggleAction());
      toggleActions.add(Separator.getInstance());
      toggleActions.add(new HideCurrentWidgetAction());
      toggleActions.add(new HideMemoryIndicatorAction());
      return CachedValueProvider.Result.create(toggleActions.toArray(AnAction.EMPTY_ARRAY), myManager);
    };
  }

  private class ToggleWidgetAction extends DumbAwareToggleAction {
    private final StatusBarWidgetFactory myWidgetFactory;

    private ToggleWidgetAction(@NotNull StatusBarWidgetFactory widgetFactory) {
      super(widgetFactory.getDisplayName());
      myWidgetFactory = widgetFactory;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      Project project = e.getProject();
      StatusBar statusBar = e.getData(PlatformDataKeys.STATUS_BAR);
      e.getPresentation().setEnabledAndVisible(project != null && statusBar != null &&
                                               myManager.canBeEnabledOnStatusBar(myWidgetFactory, statusBar));
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

  private static class MemoryIndicatorToggleAction extends DumbAwareToggleAction {
    private MemoryIndicatorToggleAction() {
      super(() -> UIBundle.message("status.bar.memory.usage.widget.name"));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return UISettings.getInstance().getShowMemoryIndicator();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      UISettings.getInstance().setShowMemoryIndicator(state);
      UISettings.getInstance().fireUISettingsChanged();
    }
  }

  private static class HideMemoryIndicatorAction extends DumbAwareAction {
    private HideMemoryIndicatorAction() {
      super(() -> UIBundle.message("status.bar.hide.widget.action.name", UIBundle.message("status.bar.memory.usage.widget.name")));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      UISettings.getInstance().setShowMemoryIndicator(false);
      UISettings.getInstance().fireUISettingsChanged();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabledAndVisible(MemoryUsagePanel.WIDGET_ID.equals(e.getData(IdeStatusBarImpl.HOVERED_WIDGET_ID)));
    }
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status.widget;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.ui.UIBundle;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

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
      return CachedValueProvider.Result.create(toggleActions.toArray(AnAction.EMPTY_ARRAY), myManager);
    };
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
      e.getPresentation().setEnabledAndVisible(project != null && statusBar != null && myManager.canBeEnabledOnStatusBar(myWidgetFactory, statusBar));
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
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status.widget;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.codeStyle.WordPrefixMatcher;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

final class StatusBarWidgetsOptionProvider implements SearchTopHitProvider {
  @Override
  public void consumeTopHits(@NotNull String pattern, @NotNull Consumer<Object> collector, @Nullable Project project) {
    if (project == null) return;

    StatusBarWidgetsManager manager = project.getService(StatusBarWidgetsManager.class);
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (statusBar == null) return;

    WordPrefixMatcher matcher = new WordPrefixMatcher(pattern);
    for (StatusBarWidgetFactory factory : manager.getWidgetFactories()) {
      if (!factory.isConfigurable() || !factory.isAvailable(project) || !manager.canBeEnabledOnStatusBar(factory, statusBar)) continue;

      String name = IdeBundle.message("label.show.status.bar.widget", factory.getDisplayName());
      if (matcher.matches(name)) collector.accept(new StatusBarWidgetOption(factory, name));
    }
  }

  private static final class StatusBarWidgetOption extends BooleanOptionDescription {
    private final StatusBarWidgetFactory myWidgetFactory;

    private StatusBarWidgetOption(StatusBarWidgetFactory factory, @Nls String name) {
      super(name, "statusBar.show.widget." + factory.getId());
      myWidgetFactory = factory;
    }

    @Override
    public boolean isOptionEnabled() {
      return StatusBarWidgetSettings.getInstance().isEnabled(myWidgetFactory);
    }

    @Override
    public void setOptionState(boolean enabled) {
      StatusBarWidgetSettings.getInstance().setEnabled(myWidgetFactory, enabled);
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        project.getService(StatusBarWidgetsManager.class).updateWidget(myWidgetFactory);
      }
    }
  }
}

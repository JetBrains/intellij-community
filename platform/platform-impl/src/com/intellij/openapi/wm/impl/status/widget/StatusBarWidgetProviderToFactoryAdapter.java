// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status.widget;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@ApiStatus.Internal
public final class StatusBarWidgetProviderToFactoryAdapter implements StatusBarWidgetFactory {
  private final Project myProject;
  private final IdeFrame myFrame;
  @SuppressWarnings("removal") final StatusBarWidgetProvider provider;

  private boolean widgetWasCreated;
  private @Nullable StatusBarWidget myWidget;

  public StatusBarWidgetProviderToFactoryAdapter(@NotNull Project project,
                                                 @NotNull IdeFrame frame,
                                                 @SuppressWarnings("removal") @NotNull StatusBarWidgetProvider provider) {
    myProject = project;
    myFrame = frame;
    this.provider = provider;
  }

  @Override
  public @NotNull String getId() {
    StatusBarWidget widget = getWidget();
    return widget != null ? widget.ID() : provider.getClass().getName();
  }

  @Override
  public @NotNull String getDisplayName() {
    StatusBarWidget widget = getWidget();
    if (widget != null) {
      StatusBarWidget.WidgetPresentation presentation = widget.getPresentation();
      String result = presentation != null ? StringUtil.notNullize(presentation.getTooltipText()) : "";
      if (!result.isEmpty()) {
        return result;
      }
      if (ApplicationManager.getApplication().isInternal()) {
        //noinspection HardCodedStringLiteral
        return widget.ID();
      }
    }
    return "";
  }

  @Override
  public boolean isAvailable(@NotNull Project project) {
    //noinspection removal
    return provider.isCompatibleWith(myFrame) && getWidget() != null;
  }

  @Override
  public boolean isConfigurable() {
    return !getDisplayName().isEmpty();
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
    return Objects.requireNonNull(getWidget());
  }

  private @Nullable StatusBarWidget getWidget() {
    if (!widgetWasCreated) {
      //noinspection removal
      myWidget = provider.getWidget(myProject);
      widgetWasCreated = true;
    }
    return myWidget;
  }

  @Override
  public void disposeWidget(@NotNull StatusBarWidget widget) {
    myWidget = null;
    widgetWasCreated = false;
    Disposer.dispose(widget);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StatusBarWidgetProviderToFactoryAdapter adapter = (StatusBarWidgetProviderToFactoryAdapter)o;
    return provider.equals(adapter.provider) && myProject.equals(adapter.myProject);
  }

  @Override
  public int hashCode() {
    return Objects.hash(provider, myProject);
  }
}

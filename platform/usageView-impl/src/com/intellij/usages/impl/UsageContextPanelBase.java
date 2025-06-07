// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.usages.impl;

import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageContextPanel;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public abstract class UsageContextPanelBase extends JBPanelWithEmptyText implements UsageContextPanel {
  protected final @NotNull UsageViewPresentation myPresentation;
  protected volatile boolean isDisposed;

  public UsageContextPanelBase(@NotNull UsageViewPresentation presentation) {
    myPresentation = presentation;
    setLayout(new BorderLayout());
    setBorder(JBUI.Borders.empty());
  }

  @Override
  public final @NotNull JComponent createComponent() {
    isDisposed = false;
    return this;
  }

  @Override
  public void dispose() {
    isDisposed = true;
  }

  protected void onEditorCreated(@NotNull Editor editor) {}

  @Override
  public final void updateLayout(@NotNull Project project, final @Nullable List<? extends UsageInfo> infos) {
    AppUIExecutor.onUiThread().withDocumentsCommitted(project).expireWith(this).execute(() -> updateLayoutLater(infos));
  }

  @Override
  public final void updateLayout(@NotNull Project project, @NotNull List<? extends UsageInfo> infos, @NotNull UsageView usageView) {
    AppUIExecutor.onUiThread().withDocumentsCommitted(project).expireWith(this)
      .execute(() -> updateLayoutLater(infos, usageView));
  }

  @Override
  @Deprecated
  public final void updateLayout(final @Nullable List<? extends UsageInfo> infos) {
    updateLayoutLater(infos);
  }

  @Override
  @Deprecated
  public final void updateLayout(@NotNull List<? extends UsageInfo> infos, @NotNull UsageView usageView) {
    updateLayoutLater(infos, usageView);
  }

  @RequiresEdt
  protected void updateLayoutLater(@NotNull List<? extends UsageInfo> infos, @NotNull UsageView usageView) {
    updateLayoutLater(infos);
  }

  protected abstract void updateLayoutLater(@Nullable List<? extends UsageInfo> infos);
}

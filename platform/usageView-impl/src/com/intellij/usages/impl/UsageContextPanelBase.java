/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
  @NotNull protected final UsageViewPresentation myPresentation;
  protected volatile boolean isDisposed;

  public UsageContextPanelBase(@NotNull UsageViewPresentation presentation) {
    myPresentation = presentation;
    setLayout(new BorderLayout());
    setBorder(JBUI.Borders.empty());
  }

  @NotNull
  @Override
  public final JComponent createComponent() {
    isDisposed = false;
    return this;
  }

  @Override
  public void dispose() {
    isDisposed = true;
  }

  protected void onEditorCreated(@NotNull Editor editor) {}

  @Override
  public final void updateLayout(@NotNull Project project, @Nullable final List<? extends UsageInfo> infos) {
    AppUIExecutor.onUiThread().withDocumentsCommitted(project).expireWith(this).execute(() -> updateLayoutLater(infos));
  }

  @Override
  public final void updateLayout(@NotNull Project project, @NotNull List<? extends UsageInfo> infos, @NotNull UsageView usageView) {
    AppUIExecutor.onUiThread().withDocumentsCommitted(project).expireWith(this)
      .execute(() -> updateLayoutLater(infos, usageView));
  }

  @Override
  @Deprecated
  public final void updateLayout(@Nullable final List<? extends UsageInfo> infos) {
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

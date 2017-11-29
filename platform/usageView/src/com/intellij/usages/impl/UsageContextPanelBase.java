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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageContextPanel;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author cdr
 */
public abstract class UsageContextPanelBase extends JBPanelWithEmptyText implements UsageContextPanel {
  protected final Project myProject;
  @NotNull protected final UsageViewPresentation myPresentation;
  protected volatile boolean isDisposed;

  public UsageContextPanelBase(@NotNull Project project, @NotNull UsageViewPresentation presentation) {
    myProject = project;
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

  @Override
  public final void updateLayout(@Nullable final List<UsageInfo> infos) {
    PsiDocumentManager pdm = PsiDocumentManager.getInstance(myProject);
    if (!pdm.hasUncommitedDocuments()) {
      updateLayoutLater(infos);
    } else {
      pdm.performLaterWhenAllCommitted(() -> {
        if (isDisposed || myProject.isDisposed()) return;
        updateLayoutLater(infos);
      });
    }

  }

  protected abstract void updateLayoutLater(@Nullable List<UsageInfo> infos);
}

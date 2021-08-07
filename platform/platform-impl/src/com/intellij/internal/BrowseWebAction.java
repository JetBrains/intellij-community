// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.jcef.JBCefApp;
import org.jetbrains.annotations.NotNull;

final class BrowseWebAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null && JBCefApp.isSupported());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      String url = Messages.showInputDialog(project, "Where to?", "Browse Web", null, "https://www.jetbrains.com", null);
      if (url != null && !url.isBlank()) {
        HTMLEditorProvider.openEditor(project, "World Wild Web", url, null);
      }
    }
  }
}

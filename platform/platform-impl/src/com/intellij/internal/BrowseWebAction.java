// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.jcef.JBCefApp;
import org.jetbrains.annotations.NotNull;

final class BrowseWebAction extends AnAction implements DumbAware {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null && JBCefApp.isSupported());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    var project = e.getProject();
    if (project != null) {
      var url = Messages.showInputDialog(project, "Where to?", "Browse Web", null, "https://www.jetbrains.com", null);
      if (url != null && !url.isBlank()) {
        HTMLEditorProvider.openEditor(project, "World Wild Web", HTMLEditorProvider.Request.url(url)
          .withQueryHandler((id, jsRequest, completion) -> {
            new Notification("System Messages", "JS request", "[" + id + "] " + jsRequest, NotificationType.INFORMATION).notify(project);
            return "true";
          }));
      }
    }
  }
}

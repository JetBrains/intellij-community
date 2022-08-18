// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ComponentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ReopenClosedTabAction extends DumbAwareAction {
  public ReopenClosedTabAction() {
    super(ActionsBundle.messagePointer("action.ReopenClosedTabAction.text"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final EditorWindow window = getEditorWindow(e);
    if (window != null) {
      if (window.hasClosedTabs()) {
        window.restoreClosedTab();
      }
      return;
    }

    Project project = e.getProject();
    if (project == null) return;
    List<VirtualFile> list = EditorHistoryManager.getInstance(project).getFileList();
    if (!list.isEmpty()) {
      FileEditorManager.getInstance(project).openFile(list.get(list.size() - 1), true);
    }
  }

  @Nullable
  private static EditorWindow getEditorWindow(@NotNull AnActionEvent e) {
    final Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    if (component != null) {
      final EditorsSplitters splitters =
        ComponentUtil.getParentOfType((Class<? extends EditorsSplitters>)EditorsSplitters.class, component);
      if (splitters != null) {
        return splitters.getCurrentWindow();
      }
    }
    return null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final EditorWindow window = getEditorWindow(e);
    if (window != null) {
      e.getPresentation().setEnabledAndVisible(window.hasClosedTabs());
      return;
    }

    Project project = e.getProject();
    if (project != null && !EditorHistoryManager.getInstance(project).getFileList().isEmpty()) {
      e.getPresentation().setEnabledAndVisible(true);
      return;
    }

    e.getPresentation().setEnabledAndVisible(false);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
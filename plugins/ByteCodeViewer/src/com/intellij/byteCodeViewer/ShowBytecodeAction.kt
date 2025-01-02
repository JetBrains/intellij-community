// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.RegisterToolWindowTaskBuilder;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import static com.intellij.byteCodeViewer.BytecodeViewerUtilKt.isValidFileType;

final class ShowBytecodeAction extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    FileType fileType = null;
    final PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
    if (file != null) {
      fileType = file.getFileType();
    }

    event.getPresentation().setEnabled(event.getProject() != null && isValidFileType(fileType));
    event.getPresentation().setIcon(AllIcons.FileTypes.JavaClass);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getProject();
    if (project == null) return;

    final VirtualFile virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE);
    final Editor editor = event.getData(CommonDataKeys.EDITOR);
    if (virtualFile == null || editor == null) return;

    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    ToolWindow toolWindow = toolWindowManager.getToolWindow(BytecodeToolWindowPanel.TOOL_WINDOW_ID);
    if (toolWindow == null) {
      toolWindow = toolWindowManager.registerToolWindow(
        BytecodeToolWindowPanel.TOOL_WINDOW_ID,
        (RegisterToolWindowTaskBuilder builder) -> {
          builder.icon = AllIcons.FileTypes.JavaClass;
          builder.anchor = ToolWindowAnchor.RIGHT;
          builder.hideOnEmptyContent = false;
          builder.canCloseContent = false;
          return Unit.INSTANCE;
        }
      );
      final ContentFactory contentFactory = ContentFactory.getInstance();
      final ContentManager contentManager = toolWindow.getContentManager();
      final BytecodeToolWindowPanel panel = new BytecodeToolWindowPanel(project, toolWindow, editor);
      contentManager.addContent(contentFactory.createContent(panel, "", false));
    }

    toolWindow.activate(null);
  }
}

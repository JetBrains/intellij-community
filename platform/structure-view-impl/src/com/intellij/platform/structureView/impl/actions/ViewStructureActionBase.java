// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.structureView.impl.actions;

import com.intellij.ide.util.FileStructureUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class ViewStructureActionBase extends DumbAwareAction {
  protected ViewStructureActionBase() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    FileEditor fileEditor = e.getData(PlatformCoreDataKeys.FILE_EDITOR);
    if (fileEditor == null) return;

    showFileStructurePopup(project, fileEditor);
  }

  protected abstract void showFileStructurePopup(@NotNull Project project,
                                                 @NotNull FileEditor fileEditor);

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    FileEditor fileEditor = e.getData(PlatformCoreDataKeys.FILE_EDITOR);
    Editor editor = fileEditor instanceof TextEditor te ? te.getEditor() :
                    e.getData(CommonDataKeys.EDITOR);

    e.getPresentation().setEnabled(isPopupAvailableFor(fileEditor, editor));
  }

  @ApiStatus.Internal
  public static boolean isPopupAvailableFor(@Nullable FileEditor fileEditor, @Nullable Editor editor) {
    return fileEditor != null &&
           (!Boolean.TRUE.equals(EditorTextField.SUPPLEMENTARY_KEY.get(editor))) &&
           (FileStructureUtil.isSplitPopupEnabled() || fileEditor.getStructureViewBuilder() != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}

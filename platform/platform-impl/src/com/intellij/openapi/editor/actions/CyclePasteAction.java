// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.datatransfer.Transferable;

public final class CyclePasteAction extends AnAction implements DumbAware {

  private long lastPasteTimeMillis = Long.MIN_VALUE;
  private int index = 0;

  public CyclePasteAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null || editor.isViewer()) return;

    long currentTimeMillis = System.currentTimeMillis();
    final ActionManager actionManager = ActionManager.getInstance();

    CopyPasteManagerEx copyPasteManager = CopyPasteManagerEx.getInstanceEx();

    final AnAction pasteAction = actionManager.getAction(IdeActions.ACTION_EDITOR_PASTE_SIMPLE);
    Transferable @NotNull [] clipboardTextTransferables = copyPasteManager.getAllContents();
    if (currentTimeMillis - lastPasteTimeMillis <= 1000L) {
      index++;
      final AnAction undoAction = actionManager.getAction(IdeActions.ACTION_UNDO);
      actionManager.tryToExecute(
        undoAction,
        e.getInputEvent(),
        editor.getComponent(),
        e.getPlace(),
        true
      );
    } else {
      index = 0;
    }
    Transferable transferable = clipboardTextTransferables[index % clipboardTextTransferables.length];
    CopyPasteManagerEx.getInstanceEx().moveContentToStackTop(transferable);
    actionManager.tryToExecute(
      pasteAction,
      e.getInputEvent(),
      editor.getComponent(),
      e.getPlace(),
      true
    );
    lastPasteTimeMillis = currentTimeMillis;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final boolean enabled = isEnabled(e);
    e.getPresentation().setEnabled(enabled);
    if (e.isFromContextMenu()) {
      e.getPresentation().setVisible(enabled);
    }
  }

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    Object component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    if (!(component instanceof JComponent)) return false;
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    return (editor != null) && !editor.isViewer();
  }
}
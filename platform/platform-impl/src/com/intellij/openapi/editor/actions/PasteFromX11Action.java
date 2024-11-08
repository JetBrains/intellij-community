// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/**
 * @author msk
 */
public final class PasteFromX11Action extends EditorAction {
  public PasteFromX11Action() {
    super(new Handler());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null || SystemInfo.isWindows || SystemInfo.isMac) {
      presentation.setEnabled(false);
    }
    else {
      boolean rightPlace = true;
      final InputEvent inputEvent = e.getInputEvent();
      if (inputEvent instanceof MouseEvent me) {
        rightPlace = false;
        if (dataContext.getData(EditorGutter.KEY) == null) {
          final Component component = SwingUtilities.getDeepestComponentAt(me.getComponent(), me.getX(), me.getY());
          rightPlace = !(component instanceof JScrollBar);
        }
      }
      presentation.setEnabled(rightPlace);
    }
  }

  public static final class Handler extends BasePasteHandler {
    @Override
    protected Transferable getContentsToPaste(Editor editor, DataContext dataContext) {
      return CopyPasteManager.getInstance().getSystemSelectionContents();
    }
  }
}

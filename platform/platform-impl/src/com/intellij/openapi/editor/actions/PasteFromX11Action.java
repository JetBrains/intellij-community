/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/**
 * @author msk
 */
public class PasteFromX11Action extends EditorAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actions.PasteFromX11Action");

  public PasteFromX11Action() {
    super(new Handler());
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null || !SystemInfo.isXWindow) {
      presentation.setEnabled(false);
    }
    else {
      boolean rightPlace = true;
      final InputEvent inputEvent = e.getInputEvent();
      if (inputEvent instanceof MouseEvent) {
        rightPlace = false;
        final MouseEvent me = (MouseEvent)inputEvent;
        if (dataContext.getData(EditorGutter.KEY) == null) {
          final Component component = SwingUtilities.getDeepestComponentAt(me.getComponent(), me.getX(), me.getY());
          rightPlace = !(component instanceof JScrollBar);
        }
      }
      presentation.setEnabled(rightPlace);
    }
  }

  public static class Handler extends BasePasteHandler {
    @Override
    protected Transferable getContentsToPaste(Editor editor, DataContext dataContext) {
      Clipboard clip = editor.getComponent().getToolkit().getSystemSelection();
      if (clip == null) return null;

      try {
        return clip.getContents(null);
      }
      catch (Exception e) {
        LOG.info(e);
        return null;
      }
    }
  }
}

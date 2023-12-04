// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.icons.ExpUiIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorMouseHoverPopupManager;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.codeFloatingToolbar.CodeFloatingToolbar;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class XDebuggerTooltipPopup {
  private final @NotNull Editor myEditor;
  private final @NotNull Point myPoint;
  private JBPopup myPopup;

  XDebuggerTooltipPopup(@NotNull Editor editor, @NotNull Point point) {
    myEditor = editor;
    myPoint = point;
  }

  @Nullable
  JBPopup show(JComponent component, @Nullable EditorMouseEvent editorMouseEvent) {
    BorderLayoutPanel content = JBUI.Panels.simplePanel();
    Color bgColor = HintUtil.getInformationColor();
    content.setBackground(bgColor);

    if (editorMouseEvent != null) {
      content.setBorder(JBUI.Borders.empty(10, 10, 10, 6));
      component.setBorder(JBUI.Borders.emptyRight(20));

      DefaultActionGroup actions = new DefaultActionGroup();
      ShowErrorsAction action = new ShowErrorsAction(editorMouseEvent);
      action.registerCustomShortcutSet(action.getShortcutSet(), content);
      actions.add(action);

      var toolbar = new ActionToolbarImpl("XDebuggerTooltip", actions, true);
      toolbar.setBorder(JBUI.Borders.emptyLeft(2));
      toolbar.setBackground(bgColor);
      toolbar.setReservePlaceAutoPopupIcon(false);
      toolbar.setTargetComponent(null);

      JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
      separator.setBackground(bgColor);
      separator.setOpaque(true);

      content.addToRight(JBUI.Panels.simplePanel(toolbar).addToLeft(separator));
    }
    else {
      content.setBorder(JBUI.Borders.empty(10));
    }

    content.addToCenter(component);

    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, component)
      .setRequestFocus(false)
      .setFocusable(false)
      .setModalContext(false)
      .setCancelOnOtherWindowOpen(true)
      .createPopup();

    //Editor may be disposed before later invokator process this action
    if (myEditor.isDisposed()) {
      myPopup.cancel();
      return null;
    }
    myPopup.show(new RelativePoint(myEditor.getContentComponent(), myPoint));
    hideAndDisableFloatingToolbar(myEditor, myPopup);
    return myPopup;
  }

  private static void hideAndDisableFloatingToolbar(@NotNull Editor editor, @NotNull JBPopup popup){
    CodeFloatingToolbar floatingToolbar = CodeFloatingToolbar.getToolbar(editor);
    if (floatingToolbar == null) return;
    floatingToolbar.hideOnPopupConflict(popup);
  }

  private class ShowErrorsAction extends AnAction {
    private final @Nullable EditorMouseEvent myEditorMouseEvent;

    ShowErrorsAction(@Nullable EditorMouseEvent editorMouseEvent) {
      super(XDebuggerBundle.message("xdebugger.show.errors.action.title"));
      // TODO: icon does not switch automatically for some reason for old/new UI
      getTemplatePresentation().setIcon(ExperimentalUI.isNewUI() ? ExpUiIcons.Status.ErrorOutline : AllIcons.Ide.FatalErrorRead);
      setShortcutSet(KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION));
      myEditorMouseEvent = editorMouseEvent;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myPopup.cancel();
      EditorMouseHoverPopupManager.getInstance().showInfoTooltip(myEditorMouseEvent);
    }
  }
}

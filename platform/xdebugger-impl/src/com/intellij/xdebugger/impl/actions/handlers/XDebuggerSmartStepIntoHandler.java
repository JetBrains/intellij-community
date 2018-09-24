/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class XDebuggerSmartStepIntoHandler extends XDebuggerSuspendedActionHandler {

  @Override
  protected boolean isEnabled(@NotNull XDebugSession session, DataContext dataContext) {
    return super.isEnabled(session, dataContext) && session.getDebugProcess().getSmartStepIntoHandler() != null;
  }

  @Override
  protected void perform(@NotNull XDebugSession session, DataContext dataContext) {
    XSmartStepIntoHandler<?> handler = session.getDebugProcess().getSmartStepIntoHandler();
    XSourcePosition position = session.getTopFramePosition();
    if (position == null || handler == null) return;

    FileEditor editor = FileEditorManager.getInstance(session.getProject()).getSelectedEditor(position.getFile());
    if (editor instanceof TextEditor) {
      doSmartStepInto(handler, position, session, ((TextEditor)editor).getEditor());
    }
  }

  private static <V extends XSmartStepIntoVariant> void doSmartStepInto(final XSmartStepIntoHandler<V> handler,
                                                                        XSourcePosition position,
                                                                        @NotNull final XDebugSession session,
                                                                        Editor editor) {
    List<V> variants = handler.computeSmartStepVariants(position);
    if (variants.isEmpty()) {
      session.stepInto();
      return;
    }
    else if (variants.size() == 1) {
      session.smartStepInto(handler, variants.get(0));
      return;
    }

    V firstTarget = variants.get(0);
    SmartStepMethodListPopupStep popupStep =
      new SmartStepMethodListPopupStep<>(handler.getPopupTitle(position), editor, variants, session, handler);

    ListPopupImpl popup = new ListPopupImpl(popupStep);
    DebuggerUIUtil.registerExtraHandleShortcuts(popup, XDebuggerActions.STEP_INTO, XDebuggerActions.SMART_STEP_INTO);
    popup.setAdText(DebuggerUIUtil.getSelectionShortcutsAdText(XDebuggerActions.STEP_INTO, XDebuggerActions.SMART_STEP_INTO));

    UIUtil.maybeInstall(popup.getList().getInputMap(JComponent.WHEN_FOCUSED),
                        "selectNextRow",
                        KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));

    popup.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        popupStep.getScopeHighlighter().dropHighlight();
        if (!e.getValueIsAdjusting()) {
          final XSmartStepIntoVariant selectedTarget = (XSmartStepIntoVariant)((JBList)e.getSource()).getSelectedValue();
          if (selectedTarget != null) {
            highlightTarget(popupStep, selectedTarget);
          }
        }
      }
    });
    highlightTarget(popupStep, firstTarget);
    DebuggerUIUtil.showPopupForEditorLine(popup, editor, position.getLine());
  }

  private static void highlightTarget(@NotNull final SmartStepMethodListPopupStep popupStep, @NotNull final XSmartStepIntoVariant target) {
    final PsiElement highlightElement = target.getHighlightElement();
    if (highlightElement != null) {
      popupStep.getScopeHighlighter().highlight(highlightElement, Collections.singletonList(highlightElement));
    }
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.components.JBList;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class XDebuggerSmartStepIntoHandler extends XDebuggerSuspendedActionHandler {

  private static final Logger LOG = Logger.getInstance(XDebuggerSmartStepIntoHandler.class);

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
                                                                        final XDebugSession session,
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

    ScopeHighlighter highlighter = new ScopeHighlighter(editor);
    ListPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<V>(handler.getPopupTitle(position), variants) {
      @Override
      public Icon getIconFor(V aValue) {
        return aValue.getIcon();
      }

      @NotNull
      @Override
      public String getTextFor(V value) {
        return value.getText();
      }

      @Override
      public PopupStep onChosen(V selectedValue, boolean finalChoice) {
        session.smartStepInto(handler, selectedValue);
        highlighter.dropHighlight();
        return FINAL_CHOICE;
      }

      @Override
      public void canceled() {
        highlighter.dropHighlight();
        super.canceled();
      }
    });
    popup.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          Object selectedValue = ObjectUtils.doIfCast(e.getSource(), JBList.class, it -> it.getSelectedValue());
          highlightVariant(ObjectUtils.tryCast(selectedValue, XSmartStepIntoVariant.class), highlighter);
        }
      }
    });
    highlightVariant(ObjectUtils.tryCast(ContainerUtil.getFirstItem(variants), XSmartStepIntoVariant.class), highlighter);
    DebuggerUIUtil.showPopupForEditorLine(popup, editor, position.getLine());
  }

  private static void highlightVariant(@Nullable XSmartStepIntoVariant variant, @NotNull ScopeHighlighter highlighter) {
    TextRange range = variant != null ? variant.getHighlightRange() : null;
    if (range != null) {
      highlighter.highlight(Pair.create(range, Collections.singletonList(range)));
    }
  }
}

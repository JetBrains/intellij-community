// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

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
  private static final Ref<Boolean> SHOW_AD = new Ref<>(true);
  private static final Logger LOG = Logger.getInstance(XDebuggerSmartStepIntoHandler.class);

  @Override
  protected boolean isEnabled(@NotNull XDebugSession session, DataContext dataContext) {
    return super.isEnabled(session, dataContext) && session.getDebugProcess().getSmartStepIntoHandler() != null;
  }

  @Override
  protected void perform(@NotNull XDebugSession session, DataContext dataContext) {
    XSmartStepIntoHandler<?> handler = session.getDebugProcess().getSmartStepIntoHandler();
    XSourcePosition position = session.getTopFramePosition();
    if (position != null && handler != null) {
      FileEditor editor = FileEditorManager.getInstance(session.getProject()).getSelectedEditor(position.getFile());
      if (editor instanceof TextEditor) {
        doSmartStepInto(handler, position, session, ((TextEditor)editor).getEditor());
        return;
      }
    }
    session.stepInto();
  }

  private <V extends XSmartStepIntoVariant> void doSmartStepInto(final XSmartStepIntoHandler<V> handler,
                                                                 XSourcePosition position,
                                                                 final XDebugSession session,
                                                                 Editor editor) {
    computeVariants(handler, position)
      .onSuccess(variants -> UIUtil.invokeLaterIfNeeded(() -> {
                                                          if (!handleSimpleCases(handler, variants, session)) {
                                                            showPopup(handler, variants, position, session, editor);
                                                          }
                                                        }))
      .onError(throwable -> session.stepInto());
  }

  protected <V extends XSmartStepIntoVariant> Promise<List<V>> computeVariants(XSmartStepIntoHandler<V> handler, XSourcePosition position) {
    return handler.computeSmartStepVariantsAsync(position);
  }

  protected <V extends XSmartStepIntoVariant> boolean handleSimpleCases(XSmartStepIntoHandler<V> handler,
                                                                        List<V> variants,
                                                                        XDebugSession session) {
    if (variants.isEmpty()) {
      handler.stepIntoEmpty(session);
      return true;
    }
    else if (variants.size() == 1) {
      session.smartStepInto(handler, variants.get(0));
      return true;
    }
    return false;
  }

  private static <V extends XSmartStepIntoVariant> void showPopup(final XSmartStepIntoHandler<V> handler,
                                                                    List<V> variants,
                                                                    XSourcePosition position,
                                                                    final XDebugSession session,
                                                                    Editor editor) {
    ScopeHighlighter highlighter = new ScopeHighlighter(editor);
    ListPopupImpl popup = new ListPopupImpl(new BaseListPopupStep<V>(handler.getPopupTitle(position), variants) {
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

    DebuggerUIUtil.registerExtraHandleShortcuts(popup, SHOW_AD, XDebuggerActions.STEP_INTO, XDebuggerActions.SMART_STEP_INTO);
    UIUtil.maybeInstall(popup.getList().getInputMap(JComponent.WHEN_FOCUSED),
                        "selectNextRow",
                        KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));

    popup.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          Object selectedValue = ObjectUtils.doIfCast(e.getSource(), JBList.class, it -> it.getSelectedValue());
          highlightVariant(ObjectUtils.tryCast(selectedValue, XSmartStepIntoVariant.class), session, editor, highlighter);
        }
      }
    });
    highlightVariant(ObjectUtils.tryCast(ContainerUtil.getFirstItem(variants), XSmartStepIntoVariant.class), session, editor, highlighter);
    DebuggerUIUtil.showPopupForEditorLine(popup, editor, position.getLine());
  }

  private static void highlightVariant(@Nullable XSmartStepIntoVariant variant,
                                       XDebugSession session,
                                       Editor editor,
                                       @NotNull ScopeHighlighter highlighter) {
    PsiElement element = variant != null ? variant.getHighlightElement() : null;
    if (element != null) {
      PsiFile currentFile = PsiDocumentManager.getInstance(session.getProject()).getPsiFile(editor.getDocument());
      PsiFile containingFile = element.getContainingFile();
      if (containingFile == null || !containingFile.getOriginalFile().equals(currentFile)) {
        LOG.error("Highlight element " + element + " is not from the current file");
      }
      highlighter.highlight(element, Collections.singletonList(element));
    }
  }
}

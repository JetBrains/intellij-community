// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@SuppressWarnings("HardCodedStringLiteral")
final class ShowEditorHighlighterTokensAction extends EditorAction {
  private static class Holder {
    private static final Key<String> TOKEN_NAME = Key.create("token.name");
    private static final Key<Boolean> LISTENER_ADDED = Key.create("token.mouse.listener.added");
    private static final TextAttributes OUR_TEXT_ATTRIBUTES = new TextAttributes(null, null,
                                                                                 JBColor.MAGENTA, EffectType.ROUNDED_BOX, Font.PLAIN);
    private static final Alarm ourAlarm = new Alarm();

    private static final EditorMouseMotionListener MOUSE_MOTION_LISTENER = new EditorMouseMotionListener() {
      @Override
      public void mouseMoved(@NotNull EditorMouseEvent e) {
        if (e.getArea() != EditorMouseEventArea.EDITING_AREA || !e.isOverText()) return;
        Editor editor = e.getEditor();
        LogicalPosition logicalPosition = e.getLogicalPosition();
        int offset = e.getOffset();
        for (RangeHighlighter highlighter : editor.getMarkupModel().getAllHighlighters()) {
          String text = highlighter.getUserData(TOKEN_NAME);
          if (!StringUtil.isEmpty(text) && (highlighter.getStartOffset() < offset && highlighter.getEndOffset() > offset ||
                                            !logicalPosition.leansForward && highlighter.getEndOffset() == offset ||
                                            logicalPosition.leansForward && highlighter.getStartOffset() == offset)) {
            int hintOffset = highlighter.getStartOffset();
            ourAlarm.cancelAllRequests();
            ourAlarm.addRequest(() -> {
              LightweightHint hint = new LightweightHint(HintUtil.createInformationLabel(text));
              Point point = HintManagerImpl.getHintPosition(hint, editor,
                                                            editor.offsetToLogicalPosition(hintOffset).leanForward(true), HintManager.ABOVE);
              ((HintManagerImpl)HintManager.getInstance()).showEditorHint(hint, editor, point, 0, 0, false);
            }, DELAY);
            break;
          }
        }
      }
    };
  }
  private static final long DELAY = 200;

  private static boolean ourEscHandlerInstalled;


  private static final class EscapeHandler extends EditorActionHandler {
    private final EditorActionHandler myDelegate;

    private EscapeHandler(EditorActionHandler delegate) {myDelegate = delegate;}

    @Override
    protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      Editor hostEditor = editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
      return hostEditor.getUserData(Holder.LISTENER_ADDED) != null || myDelegate.isEnabled(editor, caret, dataContext);
    }

    @Override
    protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      Editor hostEditor = editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
      if (hostEditor.getUserData(Holder.LISTENER_ADDED) != null) {
        cleanup(hostEditor);
      }
      else {
        myDelegate.execute(editor, caret, dataContext);
      }
    }
  }

  private ShowEditorHighlighterTokensAction() {
    super(new EditorActionHandler() {
      @Override
      protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
        return editor instanceof EditorEx;
      }

      @Override
      protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
        if (editor.getUserData(Holder.LISTENER_ADDED) != null) cleanup(editor);

        HighlighterIterator it = editor.getHighlighter().createIterator(0);
        while (!it.atEnd()) {
          RangeHighlighter h = editor.getMarkupModel().addRangeHighlighter(it.getStart(), it.getEnd(),
                                                                           0, Holder.OUR_TEXT_ATTRIBUTES, HighlighterTargetArea.EXACT_RANGE);
          IElementType tokenType = it.getTokenType();
          h.putUserData(Holder.TOKEN_NAME, String.valueOf(tokenType));
          it.advance();
        }
        editor.addEditorMouseMotionListener(Holder.MOUSE_MOTION_LISTENER);
        editor.putUserData(Holder.LISTENER_ADDED, true);

        if (!ourEscHandlerInstalled) {
          //noinspection AssignmentToStaticFieldFromInstanceMethod
          ourEscHandlerInstalled = true;
          EditorActionHandler currentHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ESCAPE);
          EditorActionManager.getInstance().setActionHandler(IdeActions.ACTION_EDITOR_ESCAPE, new EscapeHandler(currentHandler));
        }
      }
    });
  }

  private static void cleanup(Editor editor) {
    editor.putUserData(Holder.LISTENER_ADDED, null);
    editor.removeEditorMouseMotionListener(Holder.MOUSE_MOTION_LISTENER);
    for (RangeHighlighter rangeHighlighter : editor.getMarkupModel().getAllHighlighters()) {
      if (rangeHighlighter.getUserData(Holder.TOKEN_NAME) != null) rangeHighlighter.dispose();
    }
  }
}

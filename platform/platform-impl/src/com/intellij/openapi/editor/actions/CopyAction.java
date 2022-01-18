// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;

public class CopyAction extends TextComponentEditorAction implements HintManagerImpl.ActionToIgnore {

  private static final String SKIP_COPY_AND_CUT_FOR_EMPTY_SELECTION_KEY = "editor.skip.copy.and.cut.for.empty.selection";

  public CopyAction() {
    super(new Handler(), false);
  }

  public static class Handler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull final Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (!editor.getSelectionModel().hasSelection(true)) {
        if (isSkipCopyPasteForEmptySelection()) {
          return;
        }
        FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.copy.line");
        editor.getCaretModel().runForEachCaret(__ -> {
          editor.getSelectionModel().selectLineAtCaret();
          EditorActionUtil.moveCaretToLineStartIgnoringSoftWraps(editor);
        });
      }
      editor.getSelectionModel().copySelectionToClipboard();
    }
  }

  public static boolean isSkipCopyPasteForEmptySelection() {
    return AdvancedSettings.getBoolean(SKIP_COPY_AND_CUT_FOR_EMPTY_SELECTION_KEY);
  }

  public interface TransferableProvider {
    @Nullable Transferable getSelection(@NotNull Editor editor);
  }

  public static @Nullable Transferable getSelection(@NotNull Editor editor) {
    AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_COPY);
    if (!(action instanceof EditorAction)) return null;
    TransferableProvider provider = ((EditorAction)action).getHandlerOfType(TransferableProvider.class);
    return provider == null ? null : provider.getSelection(editor);
  }
}

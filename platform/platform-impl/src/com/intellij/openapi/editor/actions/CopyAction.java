// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCopyPasteHelper;
import com.intellij.openapi.editor.EditorCopyPasteHelper.CopyPasteOptions;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.ide.CopyPasteManager;
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
      copyToClipboard(editor, dataContext, EditorCopyPasteHelper.getInstance()::getSelectionTransferable);
    }
  }

  public record SelectionToCopy(@NotNull CopyPasteOptions copyPasteOptions) {
    private static final @NotNull DataKey<SelectionToCopy> KEY = DataKey.create("CopyAction.SelectionToCopy.KEY");

    public static @Nullable SelectionToCopy fromDataContext(@NotNull DataContext dataContext) {
      return KEY.getData(dataContext);
    }

    public @NotNull DataContext extendDataContext(@NotNull DataContext dataContext) {
      return dataId -> {
        if (KEY.is(dataId)) return this;
        return dataContext.getData(dataId);
      };
    }
  }

  public static @Nullable SelectionToCopy prepareSelectionToCopy(@NotNull Editor editor, boolean isToMoveCaretToSelectionStart) {
    if (editor.getSelectionModel().hasSelection(true)) {
      return new SelectionToCopy(CopyPasteOptions.DEFAULT);
    }
    if (isSkipCopyPasteForEmptySelection()) {
      return null;
    }

    editor.getCaretModel().runForEachCaret(caret -> {
      EditorActionUtil.selectEntireLines(caret);
      if (isToMoveCaretToSelectionStart && caret.hasSelection()) {
        caret.moveToVisualPosition(caret.getSelectionStartPosition());
      }
    });
    CopyPasteOptions copyPasteOptions = new CopyPasteOptions(true);
    return new SelectionToCopy(copyPasteOptions);
  }

  public static void copyToClipboard(@NotNull Editor editor,
                                     @NotNull DataContext dataContext,
                                     @NotNull TransferableProvider transferableProvider) {
    SelectionToCopy selectionToCopy = SelectionToCopy.fromDataContext(dataContext);
    if (selectionToCopy == null) {  // a genuine "Copy", not "Cut"
      selectionToCopy = prepareSelectionToCopy(editor, true);
      if (selectionToCopy == null) {
        return;
      }
      if (selectionToCopy.copyPasteOptions().isEntireLineFromEmptySelection()) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.copy.line");
      }
    }
    copyToClipboard(editor, transferableProvider, selectionToCopy);
  }

  public static void copyToClipboard(@NotNull Editor editor,
                                     @NotNull TransferableProvider transferableProvider,
                                     @NotNull SelectionToCopy selectionToCopy) {
    Transferable transferable = transferableProvider.getSelection(editor, selectionToCopy.copyPasteOptions());
    if (transferable == null) {
      return;
    }

    CopyPasteManager.getInstance().setContents(transferable);

    if (editor instanceof EditorEx) {
      EditorEx ex = (EditorEx)editor;
      if (ex.isStickySelection()) {
        ex.setStickySelection(false);
      }
    }
  }

  public static boolean isSkipCopyPasteForEmptySelection() {
    return AdvancedSettings.getBoolean(SKIP_COPY_AND_CUT_FOR_EMPTY_SELECTION_KEY);
  }

  public interface TransferableProvider {
    @Nullable Transferable getSelection(@NotNull Editor editor, @NotNull CopyPasteOptions options);
  }

  public static @Nullable Transferable getSelection(@NotNull Editor editor) {
    AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_COPY);
    if (!(action instanceof EditorAction)) return null;
    TransferableProvider provider = ((EditorAction)action).getHandlerOfType(TransferableProvider.class);
    return provider == null ? null : provider.getSelection(editor, CopyPasteOptions.DEFAULT);
  }
}

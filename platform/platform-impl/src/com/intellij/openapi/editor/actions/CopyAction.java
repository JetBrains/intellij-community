// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.EditorCopyPasteHelper.CopyPasteOptions;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;
import java.util.List;

import static com.intellij.codeInsight.highlighting.HighlightManager.HIDE_BY_ANY_KEY;
import static com.intellij.codeInsight.highlighting.HighlightManager.HIDE_BY_ESCAPE;

public final class CopyAction extends TextComponentEditorAction implements HintManagerImpl.ActionToIgnore {

  private static final String SKIP_COPY_AND_CUT_FOR_EMPTY_SELECTION_KEY = "editor.skip.copy.and.cut.for.empty.selection";
  public static final String SKIP_SELECTING_LINE_AFTER_COPY_EMPTY_SELECTION_KEY = "editor.skip.selecting.line.after.copy.empty.selection";

  public CopyAction() {
    super(new Handler(), false);
  }

  public static final class Handler extends EditorActionHandler {
    @Override
    public void doExecute(final @NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      copyToClipboard(editor, dataContext, EditorCopyPasteHelper.getInstance()::getSelectionTransferable);
    }
  }

  public record SelectionToCopy(@NotNull CopyPasteOptions copyPasteOptions,
                                @Nullable List<CaretState> caretStateToRestore) {
    private static final @NotNull DataKey<SelectionToCopy> KEY = DataKey.create("CopyAction.SelectionToCopy.KEY");

    public static @Nullable SelectionToCopy fromDataContext(@NotNull DataContext dataContext) {
      return KEY.getData(dataContext);
    }

    public @NotNull DataContext extendDataContext(@NotNull DataContext dataContext) {
      return CustomizedDataContext.withSnapshot(dataContext, sink -> {
        sink.set(KEY, this);
      });
    }
  }

  public static @Nullable SelectionToCopy prepareSelectionToCut(@NotNull Editor editor) {
    return prepareSelectionToCopy(editor, false, false);
  }

  private static @Nullable SelectionToCopy prepareSelectionToCopy(@NotNull Editor editor) {
    boolean moveCaretToSelectionStart = isCopyFromEmptySelectionToMoveCaretToLineStart();
    boolean preserveOriginalCaretState = !isCopyFromEmptySelectionToSelectLine();
    return prepareSelectionToCopy(editor, moveCaretToSelectionStart, preserveOriginalCaretState);
  }

  private static @Nullable SelectionToCopy prepareSelectionToCopy(@NotNull Editor editor,
                                                                  boolean isToMoveCaretToSelectionStart,
                                                                  boolean isToPreserveOriginalCaretState) {
    if (editor.getSelectionModel().hasSelection(true)) {
      return new SelectionToCopy(CopyPasteOptions.DEFAULT, null);
    }
    if (isSkipCopyPasteForEmptySelection()) {
      return null;
    }

    CaretModel caretModel = editor.getCaretModel();

    List<CaretState> originalCaretState = isToPreserveOriginalCaretState ? caretModel.getCaretsAndSelections() : null;
    caretModel.runForEachCaret(caret -> {
      EditorActionUtil.selectEntireLines(caret);
      if (isToMoveCaretToSelectionStart && caret.hasSelection()) {
        caret.moveToVisualPosition(caret.getSelectionStartPosition());
      }
    });
    CopyPasteOptions copyPasteOptions = new CopyPasteOptions(true);
    return new SelectionToCopy(copyPasteOptions, originalCaretState);
  }

  public static void copyToClipboard(@NotNull Editor editor,
                                     @NotNull DataContext dataContext,
                                     @NotNull TransferableProvider transferableProvider) {
    SelectionToCopy selectionToCopy = SelectionToCopy.fromDataContext(dataContext);
    if (selectionToCopy == null) {  // a genuine "Copy", not "Cut"
      selectionToCopy = prepareSelectionToCopy(editor);
      if (selectionToCopy == null) {
        return;
      }
      if (selectionToCopy.copyPasteOptions().isCopiedFromEmptySelection()) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.copy.line");
      }
    }
    copyToClipboard(editor, transferableProvider, selectionToCopy);
  }

  public static void copyToClipboard(@NotNull Editor editor,
                                     @NotNull TransferableProvider transferableProvider,
                                     @NotNull SelectionToCopy selectionToCopy) {
    Transferable transferable = transferableProvider.getSelection(editor, selectionToCopy.copyPasteOptions());
    if (transferable != null) {
      CopyPasteManager.getInstance().setContents(transferable);

      if (editor instanceof EditorEx ex) {
        if (ex.isStickySelection()) {
          ex.setStickySelection(false);
        }
      }
    }
    restoreCaretStateIfNeeded(editor, selectionToCopy);
  }

  private static void restoreCaretStateIfNeeded(@NotNull Editor editor, @NotNull SelectionToCopy selectionToCopy) {
    List<CaretState> originalCaretState = selectionToCopy.caretStateToRestore();
    if (originalCaretState != null) {
      Project project = editor.getProject();
      if (project != null) {
        highlightSelections(editor, project);
      }
      editor.getCaretModel().setCaretsAndSelections(originalCaretState);
    }
  }

  private static void highlightSelections(@NotNull Editor editor, @NotNull Project project) {
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    editor.getCaretModel().runForEachCaret(caret -> {
      if (caret.hasSelection()) {
        highlightManager.addOccurrenceHighlight(editor, caret.getSelectionStart(), caret.getSelectionEnd(),
                                                EditorColors.SEARCH_RESULT_ATTRIBUTES,
                                                HIDE_BY_ESCAPE | HIDE_BY_ANY_KEY, null);
      }
    });
  }

  public static boolean isSkipCopyPasteForEmptySelection() {
    return AdvancedSettings.getBoolean(SKIP_COPY_AND_CUT_FOR_EMPTY_SELECTION_KEY);
  }

  public static boolean isCopyFromEmptySelectionToSelectLine() {
    return !AdvancedSettings.getBoolean(SKIP_SELECTING_LINE_AFTER_COPY_EMPTY_SELECTION_KEY);
  }

  public static boolean isCopyFromEmptySelectionToMoveCaretToLineStart() {
    return Registry.is("editor.action.copy.entireLineFromEmptySelection.moveCaretToLineStart");
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

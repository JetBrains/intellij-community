// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.formatting.FormatConstants;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.MathUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

@ApiStatus.Internal
public class EditorFacadeImpl extends EditorFacade {
  private static final String WRAP_LINE_COMMAND_NAME = "AutoWrapLongLine";

  @Override
  public void runWithAnimationDisabled(@NotNull Editor editor, @NotNull Runnable taskWithScrolling) {
    EditorUtil.runWithAnimationDisabled(editor, taskWithScrolling);
  }

  @Override
  public void undo(@NotNull Project project, @NotNull FileEditor editor, @NotNull Document document, long modificationStamp) {
    UndoManager manager = UndoManager.getInstance(project);
    while (manager.isUndoAvailable(editor) && document.getModificationStamp() != modificationStamp) {
      manager.undo(editor);
    }
  }

  @Override
  public void wrapLongLinesIfNecessary(@NotNull PsiFile file,
                                       @NotNull Document document,
                                       int startOffset,
                                       int endOffset,
                                       List<? extends TextRange> enabledRanges,
                                       int rightMargin) {
    final VirtualFile vFile = FileDocumentManager.getInstance().getFile(document);
    if ((vFile == null || vFile instanceof LightVirtualFile) && !ApplicationManager.getApplication().isUnitTestMode()) {
      // we assume that control flow reaches this place when the document is backed by a "virtual" file so any changes made by
      // a formatter affect only PSI and it is out of sync with a document text
      return;
    }

    Editor editor = PsiEditorUtil.findEditor(file);
    EditorFactory editorFactory = null;
    if (editor == null) {
      if (!ApplicationManager.getApplication().isDispatchThread()) {
        return;
      }
      editorFactory = EditorFactory.getInstance();
      editor = editorFactory.createEditor(document, file.getProject(), file.getVirtualFile(), false);
    }
    try {
      final Editor editorToUse = editor;
      ApplicationManager.getApplication().runWriteAction(() -> {
        final CaretModel caretModel = editorToUse.getCaretModel();
        final int caretOffset = caretModel.getOffset();
        final RangeMarker caretMarker = editorToUse.getDocument().createRangeMarker(caretOffset, caretOffset);
        doWrapLongLinesIfNecessary(editorToUse, file.getProject(), editorToUse.getDocument(), startOffset, endOffset, enabledRanges,
                                   rightMargin);
        if (caretMarker.isValid() && caretModel.getOffset() != caretMarker.getStartOffset()) {
          caretModel.moveToOffset(caretMarker.getStartOffset());
        }
      });
    }
    finally {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
      if (documentManager.isUncommited(document)) documentManager.commitDocument(document);
      if (editorFactory != null) {
        editorFactory.releaseEditor(editor);
      }
    }
  }

  @Override
  public void doWrapLongLinesIfNecessary(@NotNull final Editor editor, @NotNull final Project project, @NotNull Document document,
                                         int startOffset, int endOffset, List<? extends TextRange> enabledRanges, int rightMargin) {
    // Normalization.
    int startOffsetToUse = MathUtil.clamp(startOffset, 0, document.getTextLength());
    int endOffsetToUse = MathUtil.clamp(endOffset, 0, document.getTextLength());

    LineWrapPositionStrategy strategy = LanguageLineWrapPositionStrategy.INSTANCE.forEditor(editor);
    CharSequence text = document.getCharsSequence();
    int startLine = document.getLineNumber(startOffsetToUse);
    int endLine = document.getLineNumber(Math.max(0, endOffsetToUse - 1));
    int maxLine = Math.min(document.getLineCount(), endLine + 1);
    int tabSize = EditorUtil.getTabSize(editor);
    if (tabSize <= 0) {
      tabSize = 1;
    }
    int spaceSize = EditorUtil.getSpaceWidth(Font.PLAIN, editor);
    int[] shifts = new int[2];
    // shifts[0] - lines shift.
    // shift[1] - offset shift.
    int cumulativeShift = 0;

    for (int line = startLine; line < maxLine; line++) {
      int startLineOffset = document.getLineStartOffset(line);
      int endLineOffset = document.getLineEndOffset(line);
      if (!canWrapLine(Math.max(startOffsetToUse, startLineOffset),
                       Math.min(endOffsetToUse, endLineOffset),
                       cumulativeShift,
                       enabledRanges)) {
        continue;
      }

      final int preferredWrapPosition
        = calculatePreferredWrapPosition(editor, text, tabSize, spaceSize, startLineOffset, endLineOffset, endOffsetToUse, rightMargin);

      if (preferredWrapPosition < 0 || preferredWrapPosition >= endLineOffset) {
        continue;
      }
      if (preferredWrapPosition >= endOffsetToUse) {
        return;
      }

      // We know that current line exceeds right margin if control flow reaches this place, so, wrap it.
      int wrapOffset = strategy.calculateWrapPosition(
        document, editor.getProject(), Math.max(startLineOffset, startOffsetToUse), Math.min(endLineOffset, endOffsetToUse),
        preferredWrapPosition, false, false
      );
      if (wrapOffset < 0 // No appropriate wrap position is found.
          // No point in splitting line when its left part contains only white spaces, example:
          //    line start -> |                   | <- right margin
          //                  |   aaaaaaaaaaaaaaaa|aaaaaaaaaaaaaaaaaaaa() <- don't want to wrap this line even if it exceeds right margin
          || CharArrayUtil.shiftBackward(text, startLineOffset, wrapOffset - 1, " \t") < startLineOffset) {
        continue;
      }

      // Move caret to the target position and emulate pressing <enter>.
      editor.getCaretModel().moveToOffset(wrapOffset);
      emulateEnter(editor, project, shifts);

      //If number of inserted symbols on new line after wrapping more or equal then symbols left on previous line
      //there was no point to wrapping it, so reverting to before wrapping version
      if (shifts[1] - 1 >= wrapOffset - startLineOffset) {
        document.deleteString(wrapOffset, wrapOffset + shifts[1]);
      }
      else {
        // We know that number of lines is just increased, hence, update the data accordingly.
        maxLine += shifts[0];
        endOffsetToUse += shifts[1];
        cumulativeShift += shifts[1];
      }

    }
  }

  private static boolean canWrapLine(int startOffset, int endOffset, int offsetShift, @NotNull List<? extends TextRange> enabledRanges) {
    for (TextRange range : enabledRanges)  {
      if (range.containsOffset(startOffset - offsetShift) && range.containsOffset(endOffset - offsetShift)) return true;
    }
    return false;
  }

  /**
   * Emulates pressing {@code Enter} at current caret position.
   *
   * @param editor       target editor
   * @param project      target project
   * @param shifts       two-elements array which is expected to be filled with the following info:
   *                       1. The first element holds added lines number;
   *                       2. The second element holds added symbols number;
   */
  private static void emulateEnter(@NotNull final Editor editor, @NotNull Project project, int[] shifts) {
    final DataContext dataContext = prepareContext(editor.getComponent(), project);
    int caretOffset = editor.getCaretModel().getOffset();
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int startSelectionOffset = 0;
    int endSelectionOffset = 0;
    boolean restoreSelection = selectionModel.hasSelection();
    if (restoreSelection) {
      startSelectionOffset = selectionModel.getSelectionStart();
      endSelectionOffset = selectionModel.getSelectionEnd();
      selectionModel.removeSelection();
    }
    int textLengthBeforeWrap = document.getTextLength();
    int lineCountBeforeWrap = document.getLineCount();

    DataManager.getInstance().saveInDataContext(dataContext, WRAP_LONG_LINE_DURING_FORMATTING_IN_PROGRESS_KEY, true);
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    try {
      Runnable command = () -> EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER)
        .execute(editor, editor.getCaretModel().getCurrentCaret(), dataContext);
      if (commandProcessor.getCurrentCommand() == null) {
        commandProcessor.executeCommand(editor.getProject(), command, WRAP_LINE_COMMAND_NAME, null);
      }
      else {
        command.run();
      }
    }
    finally {
      DataManager.getInstance().saveInDataContext(dataContext, WRAP_LONG_LINE_DURING_FORMATTING_IN_PROGRESS_KEY, null);
    }
    int symbolsDiff = document.getTextLength() - textLengthBeforeWrap;
    if (restoreSelection) {
      int newSelectionStart = startSelectionOffset;
      int newSelectionEnd = endSelectionOffset;
      if (startSelectionOffset >= caretOffset) {
        newSelectionStart += symbolsDiff;
      }
      if (endSelectionOffset >= caretOffset) {
        newSelectionEnd += symbolsDiff;
      }
      selectionModel.setSelection(newSelectionStart, newSelectionEnd);
    }
    shifts[0] = document.getLineCount() - lineCountBeforeWrap;
    shifts[1] = symbolsDiff;
  }

  /**
   * Checks if it's worth to try to wrap target line (it's long enough) and tries to calculate preferred wrap position.
   *
   * @param editor                target editor
   * @param text                  text contained at the given editor
   * @param tabSize               tab space to use (number of visual columns occupied by a tab)
   * @param spaceSize             space width in pixels
   * @param startLineOffset       start offset of the text line to process
   * @param endLineOffset         end offset of the text line to process
   * @param targetRangeEndOffset  target text region's end offset
   * @return                      negative value if no wrapping should be performed for the target line;
   *                              preferred wrap position otherwise
   */
  private static int calculatePreferredWrapPosition(@NotNull Editor editor,
                                                    @NotNull CharSequence text,
                                                    int tabSize,
                                                    int spaceSize,
                                                    int startLineOffset,
                                                    int endLineOffset,
                                                    int targetRangeEndOffset,
                                                    int rightMargin) {
    boolean hasTabs = false;
    boolean canOptimize = true;
    boolean hasNonSpaceSymbols = false;
    loop:
    for (int i = startLineOffset; i < Math.min(endLineOffset, targetRangeEndOffset); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '\t': {
          hasTabs = true;
          if (hasNonSpaceSymbols) {
            canOptimize = false;
            break loop;
          }
        }
        case ' ': break;
        default: hasNonSpaceSymbols = true;
      }
    }

    int reservedWidthInColumns = FormatConstants.getReservedLineWrapWidthInColumns(editor);

    if (!hasTabs) {
      return wrapPositionForTextWithoutTabs(startLineOffset, endLineOffset, targetRangeEndOffset, reservedWidthInColumns, rightMargin);
    }
    else if (canOptimize) {
      return wrapPositionForTabbedTextWithOptimization(text, tabSize, startLineOffset, endLineOffset, targetRangeEndOffset,
                                                       reservedWidthInColumns, rightMargin);
    }
    else {
      return wrapPositionForTabbedTextWithoutOptimization(editor, text, spaceSize, startLineOffset, endLineOffset, targetRangeEndOffset,
                                                          reservedWidthInColumns, rightMargin);
    }
  }

  private static int wrapPositionForTextWithoutTabs(int startLineOffset, int endLineOffset, int targetRangeEndOffset,
                                                    int reservedWidthInColumns, int rightMargin) {
    if (Math.min(endLineOffset, targetRangeEndOffset) - startLineOffset > rightMargin) {
      return startLineOffset + rightMargin - reservedWidthInColumns;
    }
    return -1;
  }

  private static int wrapPositionForTabbedTextWithOptimization(@NotNull CharSequence text,
                                                               int tabSize,
                                                               int startLineOffset,
                                                               int endLineOffset,
                                                               int targetRangeEndOffset,
                                                               int reservedWidthInColumns,
                                                               int rightMargin)
  {
    int width = 0;
    int symbolWidth;
    int result = Integer.MAX_VALUE;
    boolean wrapLine = false;
    for (int i = startLineOffset; i < Math.min(endLineOffset, targetRangeEndOffset); i++) {
      char c = text.charAt(i);
      symbolWidth = c == '\t' ? tabSize - (width % tabSize) : 1;
      if (width + symbolWidth + reservedWidthInColumns >= rightMargin
          && (Math.min(endLineOffset, targetRangeEndOffset) - i) >= reservedWidthInColumns)
      {
        // Remember preferred position.
        result = i - 1;
      }
      if (width + symbolWidth >= rightMargin) {
        wrapLine = true;
        break;
      }
      width += symbolWidth;
    }
    return wrapLine ? result : -1;
  }

  private static int wrapPositionForTabbedTextWithoutOptimization(@NotNull Editor editor,
                                                                  @NotNull CharSequence text,
                                                                  int spaceSize,
                                                                  int startLineOffset,
                                                                  int endLineOffset,
                                                                  int targetRangeEndOffset,
                                                                  int reservedWidthInColumns,
                                                                  int rightMargin)
  {
    int width = 0;
    int x = 0;
    int newX;
    int symbolWidth;
    int result = Integer.MAX_VALUE;
    boolean wrapLine = false;
    for (int i = startLineOffset; i < Math.min(endLineOffset, targetRangeEndOffset); i++) {
      char c = text.charAt(i);
      if (c == '\t') {
        newX = EditorUtil.nextTabStop(x, editor);
        int diffInPixels = newX - x;
        symbolWidth = diffInPixels / spaceSize;
        if (diffInPixels % spaceSize > 0) {
          symbolWidth++;
        }
      }
      else {
        newX = x + EditorUtil.charWidth(c, Font.PLAIN, editor);
        symbolWidth = 1;
      }
      if (width + symbolWidth + reservedWidthInColumns >= rightMargin
          && (Math.min(endLineOffset, targetRangeEndOffset) - i) >= reservedWidthInColumns)
      {
        result = i - 1;
      }
      if (width + symbolWidth >= rightMargin) {
        wrapLine = true;
        break;
      }
      x = newX;
      width += symbolWidth;
    }
    return wrapLine ? result : -1;
  }

  @NotNull
  private static DataContext prepareContext(@NotNull Component component, @NotNull final Project project) {
    // There is a possible case that formatting is performed from project view and editor is not opened yet. The problem is that
    // its data context doesn't contain information about project then. So, we explicitly support that here (see IDEA-72791).
    final DataContext baseDataContext = DataManager.getInstance().getDataContext(component);
    return new DelegatingDataContext(baseDataContext) {
      @Override
      public Object getData(@NotNull @NonNls String dataId) {
        Object result = baseDataContext.getData(dataId);
        if (result == null && CommonDataKeys.PROJECT.is(dataId)) {
          result = project;
        }
        return result;
      }
    };
  }

  private static class DelegatingDataContext implements DataContext, UserDataHolder {

    private final DataContext myDataContextDelegate;
    private final UserDataHolder myDataHolderDelegate;

    DelegatingDataContext(DataContext delegate) {
      myDataContextDelegate = delegate;
      if (delegate instanceof UserDataHolder) {
        myDataHolderDelegate = (UserDataHolder)delegate;
      }
      else {
        myDataHolderDelegate = null;
      }
    }

    @Override
    public Object getData(@NotNull @NonNls String dataId) {
      return myDataContextDelegate.getData(dataId);
    }

    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      return myDataHolderDelegate == null ? null : myDataHolderDelegate.getUserData(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      if (myDataHolderDelegate != null) {
        myDataHolderDelegate.putUserData(key, value);
      }
    }
  }
}

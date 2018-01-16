/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.editor.ex.util;

import com.intellij.diagnostic.Dumpable;
import com.intellij.diagnostic.LogMessageEx;
import com.intellij.ide.ui.UISettings;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.impl.ScrollingModelImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.textarea.TextComponentEditor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.messages.MessageBusConnection;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;
import java.util.List;

public final class EditorUtil {
  private static final Logger LOG = Logger.getInstance(EditorUtil.class);

  private EditorUtil() {
  }

  /**
   * @return true if the editor is in fact an ordinary file editor;
   * false if the editor is part of EditorTextField, CommitMessage and etc.
   */
  public static boolean isRealFileEditor(@Nullable Editor editor) {
    return editor != null && TextEditorProvider.getInstance().getTextEditor(editor) instanceof TextEditorImpl;
  }

  public static boolean isPasswordEditor(@Nullable Editor editor) {
    return editor != null && editor.getContentComponent() instanceof JPasswordField;
  }

  @Nullable
  public static EditorEx getEditorEx(@Nullable FileEditor fileEditor) {
    Editor editor = fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null;
    return editor instanceof EditorEx ? (EditorEx)editor : null;
  }

  public static int getLastVisualLineColumnNumber(@NotNull Editor editor, final int line) {
    if (editor instanceof EditorImpl) {
      LogicalPosition lineEndPosition = editor.visualToLogicalPosition(new VisualPosition(line, Integer.MAX_VALUE));
      int lineEndOffset = editor.logicalPositionToOffset(lineEndPosition);
      return editor.offsetToVisualPosition(lineEndOffset, true, true).column;
    }
    Document document = editor.getDocument();
    int lastLine = document.getLineCount() - 1;
    if (lastLine < 0) {
      return 0;
    }

    // Filter all lines that are not shown because of collapsed folding region.
    VisualPosition visStart = new VisualPosition(line, 0);
    LogicalPosition logStart = editor.visualToLogicalPosition(visStart);
    int lastLogLine = logStart.line;
    while (lastLogLine < document.getLineCount() - 1) {
      logStart = new LogicalPosition(logStart.line + 1, logStart.column);
      VisualPosition tryVisible = editor.logicalToVisualPosition(logStart);
      if (tryVisible.line != visStart.line) break;
      lastLogLine = logStart.line;
    }

    int resultLogLine = Math.min(lastLogLine, lastLine);
    VisualPosition resVisStart = editor.offsetToVisualPosition(document.getLineStartOffset(resultLogLine));
    VisualPosition resVisEnd = editor.offsetToVisualPosition(document.getLineEndOffset(resultLogLine));

    // Target logical line is not soft wrap affected.
    if (resVisStart.line == resVisEnd.line) {
      return resVisEnd.column;
    }

    int visualLinesToSkip = line - resVisStart.line;
    List<? extends SoftWrap> softWraps = editor.getSoftWrapModel().getSoftWrapsForLine(resultLogLine);
    for (int i = 0; i < softWraps.size(); i++) {
      SoftWrap softWrap = softWraps.get(i);
      CharSequence text = document.getCharsSequence();
      if (visualLinesToSkip <= 0) {
        VisualPosition visual = editor.offsetToVisualPosition(softWrap.getStart() - 1);
        int result = visual.column;
        int x = editor.visualPositionToXY(visual).x;
        // We need to add width of the next symbol because current result column points to the last symbol before the soft wrap.
        return  result + textWidthInColumns(editor, text, softWrap.getStart() - 1, softWrap.getStart(), x);
      }

      int softWrapLineFeeds = StringUtil.countNewLines(softWrap.getText());
      if (softWrapLineFeeds < visualLinesToSkip) {
        visualLinesToSkip -= softWrapLineFeeds;
        continue;
      }

      // Target visual column is located on the last visual line of the current soft wrap.
      if (softWrapLineFeeds == visualLinesToSkip) {
        if (i >= softWraps.size() - 1) {
          return resVisEnd.column;
        }
        // We need to find visual column for line feed of the next soft wrap.
        SoftWrap nextSoftWrap = softWraps.get(i + 1);
        VisualPosition visual = editor.offsetToVisualPosition(nextSoftWrap.getStart() - 1);
        int result = visual.column;
        int x = editor.visualPositionToXY(visual).x;

        // We need to add symbol width because current column points to the last symbol before the next soft wrap;
        result += textWidthInColumns(editor, text, nextSoftWrap.getStart() - 1, nextSoftWrap.getStart(), x);

        int lineFeedIndex = StringUtil.indexOf(nextSoftWrap.getText(), '\n');
        result += textWidthInColumns(editor, nextSoftWrap.getText(), 0, lineFeedIndex, 0);
        return result;
      }

      // Target visual column is the one before line feed introduced by the current soft wrap.
      int softWrapStartOffset = 0;
      int softWrapEndOffset = 0;
      int softWrapTextLength = softWrap.getText().length();
      while (visualLinesToSkip-- > 0) {
        softWrapStartOffset = softWrapEndOffset + 1;
        if (softWrapStartOffset >= softWrapTextLength) {
          assert false;
          return resVisEnd.column;
        }
        softWrapEndOffset = StringUtil.indexOf(softWrap.getText(), '\n', softWrapStartOffset, softWrapTextLength);
        if (softWrapEndOffset < 0) {
          assert false;
          return resVisEnd.column;
        }
      }
      VisualPosition visual = editor.offsetToVisualPosition(softWrap.getStart() - 1);
      int result = visual.column; // Column of the symbol just before the soft wrap
      int x = editor.visualPositionToXY(visual).x;

      // Target visual column is located on the last visual line of the current soft wrap.
      result += textWidthInColumns(editor, text, softWrap.getStart() - 1, softWrap.getStart(), x);
      result += calcColumnNumber(editor, softWrap.getText(), softWrapStartOffset, softWrapEndOffset);
      return result;
    }

    CharSequence editorInfo = "editor's class: " + editor.getClass()
                              + ", all soft wraps: " + editor.getSoftWrapModel().getSoftWrapsForRange(0, document.getTextLength())
                              + ", fold regions: " + Arrays.toString(editor.getFoldingModel().getAllFoldRegions());
    LogMessageEx.error(LOG, "Can't calculate last visual column", String.format(
      "Target visual line: %d, mapped logical line: %d, visual lines range for the mapped logical line: [%s]-[%s], soft wraps for "
      + "the target logical line: %s. Editor info: %s",
      line, resultLogLine, resVisStart, resVisEnd, softWraps, editorInfo
    ));

    return resVisEnd.column;
  }

  public static int getVisualLineEndOffset(@NotNull Editor editor, int line) {
    VisualPosition endLineVisualPosition = new VisualPosition(line, getLastVisualLineColumnNumber(editor, line));
    LogicalPosition endLineLogicalPosition = editor.visualToLogicalPosition(endLineVisualPosition);
    return editor.logicalPositionToOffset(endLineLogicalPosition);
  }

  public static float calcVerticalScrollProportion(@NotNull Editor editor) {
    Rectangle viewArea = editor.getScrollingModel().getVisibleAreaOnScrollingFinished();
    if (viewArea.height == 0) {
      return 0;
    }
    LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
    Point location = editor.logicalPositionToXY(pos);
    return (location.y - viewArea.y) / (float) viewArea.height;
  }

  public static void setVerticalScrollProportion(@NotNull Editor editor, float proportion) {
    Rectangle viewArea = editor.getScrollingModel().getVisibleArea();
    LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
    Point caretLocation = editor.logicalPositionToXY(caretPosition);
    int yPos = caretLocation.y;
    yPos -= viewArea.height * proportion;
    editor.getScrollingModel().scrollVertically(yPos);
  }

  public static int calcRelativeCaretPosition(@NotNull Editor editor) {
    int caretY = editor.getCaretModel().getVisualPosition().line * editor.getLineHeight();
    int viewAreaPosition = editor.getScrollingModel().getVisibleAreaOnScrollingFinished().y;
    return caretY - viewAreaPosition;
  }

  public static void setRelativeCaretPosition(@NotNull Editor editor, int position) {
    int caretY = editor.getCaretModel().getVisualPosition().line * editor.getLineHeight();
    editor.getScrollingModel().scrollVertically(caretY - position);
  }

  public static void fillVirtualSpaceUntilCaret(@NotNull Editor editor) {
    final LogicalPosition position = editor.getCaretModel().getLogicalPosition();
    fillVirtualSpaceUntil(editor, position.column, position.line);
  }

  public static void fillVirtualSpaceUntil(@NotNull final Editor editor, int columnNumber, int lineNumber) {
    final int offset = editor.logicalPositionToOffset(new LogicalPosition(lineNumber, columnNumber));
    final String filler = EditorModificationUtil.calcStringToFillVirtualSpace(editor);
    if (!filler.isEmpty()) {
      new WriteAction(){
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          editor.getDocument().insertString(offset, filler);
          editor.getCaretModel().moveToOffset(offset + filler.length());
        }
      }.execute();
    }
  }

  private static int getTabLength(int colNumber, int tabSize) {
    if (tabSize <= 0) {
      tabSize = 1;
    }
    return tabSize - colNumber % tabSize;
  }

  public static int calcColumnNumber(@NotNull Editor editor, @NotNull CharSequence text, int start, int offset) {
    return calcColumnNumber(editor, text, start, offset, getTabSize(editor));
  }

  public static int calcColumnNumber(@Nullable Editor editor, @NotNull CharSequence text, final int start, final int offset, final int tabSize) {
    if (editor instanceof TextComponentEditor) {
      return offset - start;
    }
    boolean useOptimization = true;
    if (editor != null) {
      SoftWrap softWrap = editor.getSoftWrapModel().getSoftWrap(start);
      useOptimization = softWrap == null;
    }
    if (useOptimization) {
      boolean hasNonTabs = false;
      for (int i = start; i < offset; i++) {
        if (text.charAt(i) == '\t') {
          if (hasNonTabs) {
            useOptimization = false;
            break;
          }
        }
        else {
          hasNonTabs = true;
        }
      }
    }

    if (editor != null && useOptimization) {
      Document document = editor.getDocument();
      if (start < offset - 1 && document.getLineNumber(start) != document.getLineNumber(offset - 1)) {
        String editorInfo = editor instanceof EditorImpl ? ". Editor info: " + ((EditorImpl)editor).dumpState() : "";
        String documentInfo;
        if (text instanceof Dumpable) {
          documentInfo = ((Dumpable)text).dumpState();
        }
        else {
          documentInfo = "Text holder class: " + text.getClass();
        }
        LogMessageEx.error(
          LOG, "detected incorrect offset -> column number calculation",
          "start: " + start + ", given offset: " + offset+", given tab size: " + tabSize + ". "+documentInfo+ editorInfo);
      }
    }

    int shift = 0;
    for (int i = start; i < offset; i++) {
      char c = text.charAt(i);
      if (c == '\t') {
        shift += getTabLength(i + shift - start, tabSize) - 1;
      }
    }
    return offset - start + shift;
  }

  public static void setHandCursor(@NotNull Editor view) {
    Cursor c = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    // XXX: Workaround, simply view.getContentComponent().setCursor(c) doesn't work
    if (view.getContentComponent().getCursor() != c) {
      view.getContentComponent().setCursor(c);
    }
  }

  @NotNull
  public static FontInfo fontForChar(final char c, @JdkConstants.FontStyle int style, @NotNull Editor editor) {
    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    return ComplementaryFontsRegistry.getFontAbleToDisplay(c, style, colorsScheme.getFontPreferences(),
                                                           FontInfo.getFontRenderContext(editor.getContentComponent()));
  }

  public static Icon scaleIconAccordingEditorFont(Icon icon, Editor editor) {
    if (Registry.is("editor.scale.gutter.icons") && editor instanceof EditorImpl && icon instanceof ScalableIcon) {
      float scale = ((EditorImpl)editor).getScale();
      if (Math.abs(1f - scale) > 0.1f) {
        return ((ScalableIcon)icon).scale(scale);
      }
    }
    return icon;
  }

  public static int charWidth(char c, @JdkConstants.FontStyle int fontType, @NotNull Editor editor) {
    return fontForChar(c, fontType, editor).charWidth(c);
  }

  public static int getSpaceWidth(@JdkConstants.FontStyle int fontType, @NotNull Editor editor) {
    int width = charWidth(' ', fontType, editor);
    return width > 0 ? width : 1;
  }

  public static int getPlainSpaceWidth(@NotNull Editor editor) {
    return getSpaceWidth(Font.PLAIN, editor);
  }

  public static int getTabSize(@NotNull Editor editor) {
    return editor.getSettings().getTabSize(editor.getProject());
  }

  public static int nextTabStop(int x, @NotNull Editor editor) {
    int tabSize = getTabSize(editor);
    if (tabSize <= 0) {
      tabSize = 1;
    }
    return nextTabStop(x, editor, tabSize);
  }

  public static int nextTabStop(int x, @NotNull Editor editor, int tabSize) {
    int leftInset = editor.getContentComponent().getInsets().left;
    return nextTabStop(x - leftInset, getSpaceWidth(Font.PLAIN, editor), tabSize) + leftInset;
  }

  public static int nextTabStop(int x, int plainSpaceWidth, int tabSize) {
    if (tabSize <= 0) {
      return x + plainSpaceWidth;
    }
    tabSize *= plainSpaceWidth;

    int nTabs = x / tabSize;
    return (nTabs + 1) * tabSize;
  }

  public static float nextTabStop(float x, float plainSpaceWidth, int tabSize) {
    if (tabSize <= 0) {
      return x + plainSpaceWidth;
    }
    tabSize *= plainSpaceWidth;

    int nTabs = (int) (x / tabSize);
    return (nTabs + 1) * tabSize;
  }

  public static int textWidthInColumns(@NotNull Editor editor, @NotNull CharSequence text, int start, int end, int x) {
    int startToUse = start;
    int lastTabSymbolIndex = -1;

    // Skip all lines except the last.
    loop:
    for (int i = end - 1; i >= start; i--) {
      switch (text.charAt(i)) {
        case '\n': startToUse = i + 1; break loop;
        case '\t': if (lastTabSymbolIndex < 0) lastTabSymbolIndex = i;
      }
    }

    // Tabulation is assumed to be the only symbol which representation may take various number of visual columns, hence,
    // we return eagerly if no such symbol is found.
    if (lastTabSymbolIndex < 0) {
      return end - startToUse;
    }

    int result = 0;
    int spaceSize = getSpaceWidth(Font.PLAIN, editor);

    // Calculate number of columns up to the latest tabulation symbol.
    for (int i = startToUse; i <= lastTabSymbolIndex; i++) {
      SoftWrap softWrap = editor.getSoftWrapModel().getSoftWrap(i);
      if (softWrap != null) {
        x = softWrap.getIndentInPixels();
      }
      char c = text.charAt(i);
      int prevX = x;
      switch (c) {
        case '\t':
          x = nextTabStop(x, editor);
          result += columnsNumber(x - prevX, spaceSize);
          break;
        case '\n': x = result = 0; break;
        default: x += charWidth(c, Font.PLAIN, editor); result++;
      }
    }

    // Add remaining tabulation-free columns.
    result += end - lastTabSymbolIndex - 1;
    return result;
  }

  /**
   * Allows to answer how many visual columns are occupied by the given width.
   *
   * @param width       target width
   * @param plainSpaceSize   width of the single space symbol within the target editor (in plain font style)
   * @return            number of visual columns are occupied by the given width
   */
  public static int columnsNumber(int width, int plainSpaceSize) {
    int result = width / plainSpaceSize;
    if (width % plainSpaceSize > 0) {
      result++;
    }
    return result;
  }

  public static int columnsNumber(float width, float plainSpaceSize) {
    return (int)Math.ceil(width / plainSpaceSize);
  }

  /**
   * Allows to answer what width in pixels is required to draw fragment of the given char array from {@code [start; end)} interval
   * at the given editor.
   * <p/>
   * Tabulation symbols is processed specially, i.e. it's ta
   * <p/>
   * <b>Note:</b> it's assumed that target text fragment remains to the single line, i.e. line feed symbols within it are not
   * treated specially.
   *
   * @param editor    editor that will be used for target text representation
   * @param text      target text holder
   * @param start     offset within the given char array that points to target text start (inclusive)
   * @param end       offset within the given char array that points to target text end (exclusive)
   * @param fontType  font type to use for target text representation
   * @param x         {@code 'x'} coordinate that should be used as a starting point for target text representation.
   *                  It's necessity is implied by the fact that IDEA editor may represent tabulation symbols in any range
   *                  from {@code [1; tab size]} (check {@link #nextTabStop(int, Editor)} for more details)
   * @return          width in pixels required for target text representation
   */
  public static int textWidth(@NotNull Editor editor, @NotNull CharSequence text, int start, int end, @JdkConstants.FontStyle int fontType, int x) {
    int result = 0;
    for (int i = start; i < end; i++) {
      char c = text.charAt(i);
      if (c != '\t') {
        FontInfo font = fontForChar(c, fontType, editor);
        result += font.charWidth(c);
        continue;
      }

      result += nextTabStop(x + result, editor) - result - x;
    }
    return result;
  }

  /**
   * Delegates to the {@link #calcSurroundingRange(Editor, VisualPosition, VisualPosition)} with the
   * {@link CaretModel#getVisualPosition() caret visual position} as an argument.
   *
   * @param editor  target editor
   * @return        surrounding logical positions
   * @see #calcSurroundingRange(Editor, VisualPosition, VisualPosition)
   */
  public static Pair<LogicalPosition, LogicalPosition> calcCaretLineRange(@NotNull Editor editor) {
    return calcSurroundingRange(editor, editor.getCaretModel().getVisualPosition(), editor.getCaretModel().getVisualPosition());
  }

  public static Pair<LogicalPosition, LogicalPosition> calcCaretLineRange(@NotNull Caret caret) {
    return calcSurroundingRange(caret.getEditor(), caret.getVisualPosition(), caret.getVisualPosition());
  }

  /**
   * Calculates logical positions that surround given visual positions and conform to the following criteria:
   * <pre>
   * <ul>
   *   <li>located at the start or the end of the visual line;</li>
   *   <li>doesn't have soft wrap at the target offset;</li>
   * </ul>
   * </pre>
   * Example:
   * <pre>
   *   first line [soft-wrap] some [start-position] text [end-position] [fold-start] fold line 1
   *   fold line 2
   *   fold line 3[fold-end] [soft-wrap] end text
   * </pre>
   * The very first and the last positions will be returned here.
   *
   * @param editor    target editor to use
   * @param start     target start coordinate
   * @param end       target end coordinate
   * @return          pair of the closest surrounding non-soft-wrapped logical positions for the visual line start and end
   *
   * @see #getNotFoldedLineStartOffset(Editor, int)
   * @see #getNotFoldedLineEndOffset(Editor, int)
   */
  @SuppressWarnings("AssignmentToForLoopParameter")
  public static Pair<LogicalPosition, LogicalPosition> calcSurroundingRange(@NotNull Editor editor,
                                                                            @NotNull VisualPosition start,
                                                                            @NotNull VisualPosition end) {
    final Document document = editor.getDocument();
    final FoldingModel foldingModel = editor.getFoldingModel();

    LogicalPosition first = editor.visualToLogicalPosition(new VisualPosition(start.line, 0));
    for (
      int line = first.line, offset = document.getLineStartOffset(line);
      offset >= 0;
      offset = document.getLineStartOffset(line)) {
      final FoldRegion foldRegion = foldingModel.getCollapsedRegionAtOffset(offset);
      if (foldRegion == null) {
        first = new LogicalPosition(line, 0);
        break;
      }
      final int foldEndLine = document.getLineNumber(foldRegion.getStartOffset());
      if (foldEndLine <= line) {
        first = new LogicalPosition(line, 0);
        break;
      }
      line = foldEndLine;
    }


    LogicalPosition second = editor.visualToLogicalPosition(new VisualPosition(end.line, 0));
    for (
      int line = second.line, offset = document.getLineEndOffset(line);
      offset <= document.getTextLength();
      offset = document.getLineEndOffset(line)) {
      final FoldRegion foldRegion = foldingModel.getCollapsedRegionAtOffset(offset);
      if (foldRegion == null) {
        second = new LogicalPosition(line + 1, 0);
        break;
      }
      final int foldEndLine = document.getLineNumber(foldRegion.getEndOffset());
      if (foldEndLine <= line) {
        second = new LogicalPosition(line + 1, 0);
        break;
      }
      line = foldEndLine;
    }

    if (second.line >= document.getLineCount()) {
      second = editor.offsetToLogicalPosition(document.getTextLength());
    }
    return Pair.create(first, second);
  }

  /**
   * Finds the start offset of visual line at which given offset is located, not taking soft wraps into account.
   */
  public static int getNotFoldedLineStartOffset(@NotNull Editor editor, int offset) {
    while(true) {
      offset = DocumentUtil.getLineStartOffset(offset, editor.getDocument());
      FoldRegion foldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(offset - 1);
      if (foldRegion == null || foldRegion.getStartOffset() >= offset) {
        break;
      }
      offset = foldRegion.getStartOffset();
    }
    return offset;
  }

  /**
   * Finds the end offset of visual line at which given offset is located, not taking soft wraps into account.
   */
  public static int getNotFoldedLineEndOffset(@NotNull Editor editor, int offset) {
    while(true) {
      offset = getLineEndOffset(offset, editor.getDocument());
      FoldRegion foldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(offset);
      if (foldRegion == null || foldRegion.getEndOffset() <= offset) {
        break;
      }
      offset = foldRegion.getEndOffset();
    }
    return offset;
  }

  private static int getLineEndOffset(int offset, Document document) {
    if (offset >= document.getTextLength()) {
      return offset;
    }
    int lineNumber = document.getLineNumber(offset);
    return document.getLineEndOffset(lineNumber);
  }

  public static void scrollToTheEnd(@NotNull Editor editor) {
    scrollToTheEnd(editor, false);
  }

  public static void scrollToTheEnd(@NotNull Editor editor, boolean preferVerticalScroll) {
    editor.getSelectionModel().removeSelection();
    Document document = editor.getDocument();
    int lastLine = Math.max(0, document.getLineCount() - 1);
    if (editor.getCaretModel().getLogicalPosition().line == lastLine) {
      editor.getCaretModel().moveToOffset(document.getTextLength());
    } else {
      editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(lastLine, 0));
    }
    ScrollingModel scrollingModel = editor.getScrollingModel();
    if (preferVerticalScroll && document.getLineStartOffset(lastLine) == document.getLineEndOffset(lastLine)) {
      // don't move 'focus' to empty last line
      int scrollOffset;
      if (editor instanceof EditorEx) {
        JScrollBar verticalScrollBar = ((EditorEx)editor).getScrollPane().getVerticalScrollBar();
        scrollOffset = verticalScrollBar.getMaximum() - verticalScrollBar.getModel().getExtent();
      }
      else {
        scrollOffset = editor.getContentComponent().getHeight() - scrollingModel.getVisibleArea().height;
      }
      scrollingModel.scrollVertically(scrollOffset);
    }
    else {
      scrollingModel.scrollToCaret(ScrollType.RELATIVE);
    }
  }

  public static boolean isChangeFontSize(@NotNull MouseWheelEvent e) {
    if (e.getWheelRotation() == 0) return false;
    return SystemInfo.isMac
           ? !e.isControlDown() && e.isMetaDown() && !e.isAltDown() && !e.isShiftDown()
           : e.isControlDown() && !e.isMetaDown() && !e.isAltDown() && !e.isShiftDown();
  }

  public static boolean inVirtualSpace(@NotNull Editor editor, @NotNull LogicalPosition logicalPosition) {
    return !editor.offsetToLogicalPosition(editor.logicalPositionToOffset(logicalPosition)).equals(logicalPosition);
  }

  public static void reinitSettings() {
    EditorFactory.getInstance().refreshAllEditors();
  }

  @NotNull
  public static TextRange getSelectionInAnyMode(Editor editor) {
    SelectionModel selection = editor.getSelectionModel();
    int[] starts = selection.getBlockSelectionStarts();
    int[] ends = selection.getBlockSelectionEnds();
    int start = starts.length > 0 ? starts[0] : selection.getSelectionStart();
    int end = ends.length > 0 ? ends[ends.length - 1] : selection.getSelectionEnd();
    return TextRange.create(start, end);
  }

  public static int yPositionToLogicalLine(@NotNull Editor editor, @NotNull MouseEvent event) {
    return yPositionToLogicalLine(editor, event.getY());
  }

  public static int yPositionToLogicalLine(@NotNull Editor editor, @NotNull Point point) {
    return yPositionToLogicalLine(editor, point.y);
  }

  public static int yPositionToLogicalLine(@NotNull Editor editor, int y) {
    int line = editor instanceof EditorImpl ? ((EditorImpl)editor).yToVisibleLine(y): y / editor.getLineHeight();
    return line > 0 ? editor.visualToLogicalPosition(new VisualPosition(line, 0)).line : 0;
  }

  public static boolean isAtLineEnd(@NotNull Editor editor, int offset) {
    Document document = editor.getDocument();
    if (offset < 0 || offset > document.getTextLength()) {
      return false;
    }
    int line = document.getLineNumber(offset);
    return offset == document.getLineEndOffset(line);
  }

  /**
   * Setting selection using {@link SelectionModel#setSelection(int, int)} or {@link Caret#setSelection(int, int)} methods can result
   * in resulting selection range to be larger than requested (in case requested range intersects with collapsed fold regions).
   * This method will make sure interfering collapsed regions are expanded first, so that resulting selection range is exactly as 
   * requested.
   */
  public static void setSelectionExpandingFoldedRegionsIfNeeded(@NotNull Editor editor, int startOffset, int endOffset) {
    FoldingModel foldingModel = editor.getFoldingModel();
    FoldRegion startFoldRegion = foldingModel.getCollapsedRegionAtOffset(startOffset);
    if (startFoldRegion != null && (startFoldRegion.getStartOffset() == startOffset || startFoldRegion.isExpanded())) {
      startFoldRegion = null;
    }
    FoldRegion endFoldRegion = foldingModel.getCollapsedRegionAtOffset(endOffset);
    if (endFoldRegion != null && (endFoldRegion.getStartOffset() == endOffset || endFoldRegion.isExpanded())) {
      endFoldRegion = null;
    }
    if (startFoldRegion != null || endFoldRegion != null) {
      final FoldRegion finalStartFoldRegion = startFoldRegion;
      final FoldRegion finalEndFoldRegion = endFoldRegion;
      foldingModel.runBatchFoldingOperation(() -> {
        if (finalStartFoldRegion != null) finalStartFoldRegion.setExpanded(true);
        if (finalEndFoldRegion != null) finalEndFoldRegion.setExpanded(true);
      });
    }
    editor.getSelectionModel().setSelection(startOffset, endOffset);
  }

  public static Font getEditorFont() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    int size = UISettings.getInstance().getPresentationMode()
               ? UISettings.getInstance().getPresentationModeFontSize() - 4 : scheme.getEditorFontSize();
    return new Font(scheme.getEditorFontName(), Font.PLAIN, size);
  }

  /**
   * Number of virtual soft wrap introduced lines on a current logical line before the visual position that corresponds
   * to the current logical position.
   *
   * @see LogicalPosition#softWrapLinesOnCurrentLogicalLine
   */
  public static int getSoftWrapCountAfterLineStart(@NotNull Editor editor, @NotNull LogicalPosition position) {
    if (position.visualPositionAware) {
      return position.softWrapLinesOnCurrentLogicalLine;
    }
    int startOffset = editor.getDocument().getLineStartOffset(position.line);
    int endOffset = editor.logicalPositionToOffset(position);
    return editor.getSoftWrapModel().getSoftWrapsForRange(startOffset, endOffset).size();
  }

  public static boolean attributesImpactFontStyleOrColor(@Nullable TextAttributes attributes) {
    return attributes == TextAttributes.ERASE_MARKER ||
           (attributes != null && (attributes.getFontType() != Font.PLAIN || attributes.getForegroundColor() != null));
  }

  public static boolean isCurrentCaretPrimary(@NotNull Editor editor) {
    return editor.getCaretModel().getCurrentCaret() == editor.getCaretModel().getPrimaryCaret();
  }

  public static void disposeWithEditor(@NotNull Editor editor, @NotNull Disposable disposable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (Disposer.isDisposed(disposable)) return;
    if (editor.isDisposed()) {
      Disposer.dispose(disposable);
      return;
    }
    // for injected editors disposal will happen only when host editor is disposed,
    // but this seems to be the best we can do (there are no notifications on disposal of injected editor)
    Editor hostEditor = editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
    EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryAdapter() {
      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        if (event.getEditor() == hostEditor) {
          Disposer.dispose(disposable);
        }
      }
    }, disposable);
  }

  public static void runBatchFoldingOperationOutsideOfBulkUpdate(@NotNull Editor editor, @NotNull Runnable operation) {
    DocumentEx document = ObjectUtils.tryCast(editor.getDocument(), DocumentEx.class);
    if (document != null && document.isInBulkUpdate()) {
      MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
      disposeWithEditor(editor, connection);
      connection.subscribe(DocumentBulkUpdateListener.TOPIC, new DocumentBulkUpdateListener.Adapter() {
        @Override
        public void updateFinished(@NotNull Document doc) {
          if (doc == editor.getDocument()) {
            editor.getFoldingModel().runBatchFoldingOperation(operation);
            connection.disconnect();
          }
        }
      });
    }
    else {
      editor.getFoldingModel().runBatchFoldingOperation(operation);
    }
  }

  public static void runWithAnimationDisabled(@NotNull Editor editor, @NotNull Runnable taskWithScrolling) {
    ScrollingModel scrollingModel = editor.getScrollingModel();
    if (!(scrollingModel instanceof ScrollingModelImpl)) {
      taskWithScrolling.run();
    }
    else {
      boolean animationWasEnabled = ((ScrollingModelImpl)scrollingModel).isAnimationEnabled();
      scrollingModel.disableAnimation();
      try {
        taskWithScrolling.run();
      }
      finally {
        if (animationWasEnabled) scrollingModel.enableAnimation();
      }
    }
  }

  @NotNull
  public static String displayCharInEditor(char c, @NotNull TextAttributesKey textAttributesKey, @NotNull String fallback) {
    int codePoint = (int)c;
    if (!Character.isValidCodePoint(codePoint)) {
      return fallback;
    }

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes textAttributes = scheme.getAttributes(textAttributesKey);
    int style = textAttributes != null ? textAttributes.getFontType() : Font.PLAIN;
    FontInfo fallbackFont = ComplementaryFontsRegistry.getFontAbleToDisplay((int)c, style, scheme.getFontPreferences(), null);
    return fallbackFont.canDisplay(codePoint) ? String.valueOf(c) : fallback;
  }
}

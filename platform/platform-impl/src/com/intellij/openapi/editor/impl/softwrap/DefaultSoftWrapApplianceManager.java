/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.nio.CharBuffer;

/**
 * Default {@link SoftWrapApplianceManager} implementation that is built with the following design guide lines:
 * <pre>
 * <ul>
 *   <li>
 *      perform soft wrap processing per-logical line, i.e. every time current manager is asked to process
 *      particular text range, it calculates logical lines that contain all target symbols, checks if they should
 *      be soft-wrapped and registers corresponding soft wraps if necessary;
 *   </li>
 *   <li>
 *      objects of this class remember processed logical lines and perform new processing for them only if visible
 *      area width is changed;
 *   </li>
 *   <li>
 *      {@link SoftWrapsStorage#removeAll() drops all registered soft wraps} if visible area width is changed;
 *   </li>
 * </ul>
 * </pre>
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jul 5, 2010 10:01:27 AM
 */
public class DefaultSoftWrapApplianceManager implements SoftWrapApplianceManager {

  /** Enumerates possible type of soft wrap indents to use. */
  enum IndentType {
    /** Don't apply special indent to soft-wrapped line at all. */
    NONE,

    /**
     * Indent soft wraps for the {@link EditorSettings#getCustomSoftWrapIndent() user-defined number of columns}
     * to the start of the previous visual line.
     */
    CUSTOM,

    /**
     * Tries to indents soft-wrapped line start to the location of the first non white-space symbols of the previous visual line.
     * <p/>
     * Falls back to {@link #NONE} if indentation to the previous visual line start is considered to be unappropriated.
     */
    AUTOMATIC
  }

  private static final int DEFAULT_INDENT_SIZE = 4;

  /** Contains white space characters (has special treatment during soft wrap position calculation). */
  private static final TIntHashSet WHITE_SPACES = new TIntHashSet();
  static {
    WHITE_SPACES.add(' ');
    WHITE_SPACES.add('\t');
  }

  /**
   * Contains symbols that are special in that soft wrap is allowed to be performed only
   * after them (not before).
   */
  private static final TIntHashSet SPECIAL_SYMBOLS_TO_WRAP_AFTER = new TIntHashSet();
  static {
    SPECIAL_SYMBOLS_TO_WRAP_AFTER.add(',');
    SPECIAL_SYMBOLS_TO_WRAP_AFTER.add(';');
    SPECIAL_SYMBOLS_TO_WRAP_AFTER.add(')');
  }

  /**
   * Contains symbols that are special in that soft wrap is allowed to be performed only
   * before them (not after).
   */
  private static final TIntHashSet SPECIAL_SYMBOLS_TO_WRAP_BEFORE = new TIntHashSet();
  static {
    SPECIAL_SYMBOLS_TO_WRAP_BEFORE.add('(');
    SPECIAL_SYMBOLS_TO_WRAP_BEFORE.add('.');
  }

  /**
   * Holds information about logical lines for which soft wrap is calculated as a set of
   * <code>(logical line number; temporary)</code> pairs.
   */
  private final TIntObjectHashMap<Boolean> myProcessedLogicalLines = new TIntObjectHashMap<Boolean>();

  private final DocumentListener myDocumentListener = new LineOrientedDocumentChangeAdapter() {
    @Override
    public void beforeDocumentChange(int startLine, int endLine, int symbolsDifference) {
      dropData(startLine, endLine);
    }

    @Override
    public void afterDocumentChange(int startLine, int endLine, int symbolsDifference) {
      dropData(startLine, endLine);
    }

    @Override
    public int getPriority() {
      return SoftWrapConstants.SOFT_WRAP_APPLIANCE_LISTENER_PRIORITY;
    }

    private void dropData(int startLine, int endLine) {
      Document document = myEditor.getDocument();
      for (int i = startLine; i <= endLine; i++) {
        myProcessedLogicalLines.remove(i);

        // Calculate approximate soft wraps positions using plain font.
        // Note: we don't update 'myProcessedLogicalLines' collection here, i.e. soft wraps will be recalculated precisely
        // during standard editor repainting iteration.
        if (i < document.getLineCount()) {
          processLogicalLine(document.getCharsSequence(), i, Font.PLAIN, IndentType.NONE, true);
        }
      }
    }
  };

  private final EditorTextRepresentationHelper myTextRepresentationHelper;
  private final SoftWrapsStorage               myStorage;
  private final EditorEx                       myEditor;
  private final SoftWrapPainter                myPainter;

  private boolean myCustomIndentUsedLastTime;
  private int myCustomIndentValueUsedLastTime;
  private int myVisibleAreaWidth;

  public DefaultSoftWrapApplianceManager(SoftWrapsStorage storage,
                                         EditorEx editor,
                                         SoftWrapPainter painter,
                                         EditorTextRepresentationHelper textRepresentationHelper)
  {
    myStorage = storage;
    myEditor = editor;
    myPainter = painter;
    myTextRepresentationHelper = textRepresentationHelper;
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  @Override
  public void registerSoftWrapIfNecessary(@NotNull CharSequence text, int start, int end, int x, int fontType, boolean temporary) {
    dropDataIfNecessary();

    if (myVisibleAreaWidth <= 0 || start >= end) {
      return;
    }

    IndentType indent = getIndentToUse();
    boolean useCustomIndent = indent == IndentType.CUSTOM;
    int currentCustomIndent = myEditor.getSettings().getCustomSoftWrapIndent();
    if (useCustomIndent ^ myCustomIndentUsedLastTime || (useCustomIndent && myCustomIndentValueUsedLastTime != currentCustomIndent)) {
      myProcessedLogicalLines.clear();
    }
    myCustomIndentUsedLastTime = useCustomIndent;
    myCustomIndentValueUsedLastTime = currentCustomIndent;

    Document document = myEditor.getDocument();
    int startLine = document.getLineNumber(start);
    int endLine = document.getLineNumber(end);
    for (int i = startLine; i <= endLine; i++) {
      if (!myProcessedLogicalLines.contains(i) || (!temporary && myProcessedLogicalLines.get(i))) {
        processLogicalLine(text, i, fontType, indent, temporary);
        myProcessedLogicalLines.put(i, temporary);
      }
    }
  }

  private IndentType getIndentToUse() {
    if (myEditor.getSettings().isUseCustomSoftWrapIndent()) {
      return IndentType.CUSTOM;
    }
    return !myEditor.isViewer() && !myEditor.getDocument().isWritable() ? IndentType.AUTOMATIC : IndentType.NONE;
  }

  public DocumentListener getDocumentListener() {
    return myDocumentListener;
  }

  private void dropDataIfNecessary() {
    int currentVisibleAreaWidth = myEditor.getScrollingModel().getVisibleArea().width;
    if (myVisibleAreaWidth == currentVisibleAreaWidth) {
      return;
    }

    // Drop information about processed lines then.
    myProcessedLogicalLines.clear();
    myStorage.removeAll();
    myVisibleAreaWidth = currentVisibleAreaWidth;
  }

  private void processLogicalLine(CharSequence text, int line, int fontType, IndentType indentType, boolean temporary) {
    Document document = myEditor.getDocument();
    int startOffset = document.getLineStartOffset(line);
    int endOffset = document.getLineEndOffset(line);

    // There is a possible case that this method is called for the approximate soft wraps positions calculation. E.g. the
    // user can insert a long string to the end of the document and we don't want to perform horizontal scrolling to its end.
    // Hence, we approximately define soft wraps for the inserted text assuming that there precise calculation will be performed
    // on regular editor repainting iteration. However, we need to drop all those temporary soft wraps registered for
    // the same line before.
    myStorage.removeInRange(startOffset, endOffset + 1/* add 1 to handle possible situation when soft wrap is registered at the line end */);

    if (indentType == IndentType.NONE) {
      TIntArrayList offsets = calculateSoftWrapOffsets(text, startOffset, endOffset, fontType, 0);
      registerSoftWraps(offsets, 0, temporary);
      return;
    }

    // Understand if it's worth to define indent for soft wrap(s) to create and perform their actual construction and registration.
    int prevLineIndentInColumns = 0;
    
    int firstNonSpaceSymbolIndex = startOffset;
    for (; firstNonSpaceSymbolIndex < endOffset; firstNonSpaceSymbolIndex++) {
      char c = text.charAt(firstNonSpaceSymbolIndex);
      if (c != ' ' && c != '\t') {
        break;
      }
    }
    if (firstNonSpaceSymbolIndex > startOffset) {
      prevLineIndentInColumns = myTextRepresentationHelper.toVisualColumnSymbolsNumber(text, startOffset, firstNonSpaceSymbolIndex, 0);
    }

    int spaceWidth = EditorUtil.getSpaceWidth(fontType, myEditor);
    if (indentType == IndentType.CUSTOM) {
      int indentInColumns = myEditor.getSettings().getCustomSoftWrapIndent();
      TIntArrayList offsets = calculateSoftWrapOffsets(
        text, startOffset, endOffset, fontType, (indentInColumns + prevLineIndentInColumns) * spaceWidth
      );
      registerSoftWraps(offsets, indentInColumns + prevLineIndentInColumns, temporary);
      return;
    }

    int indentInColumns = getIndentSize();
    int indentInColumnsToUse = 0;
    TIntArrayList softWrapOffsetsToUse = null;
    for (; indentInColumns >= 0; indentInColumns--) {
      TIntArrayList offsets = calculateSoftWrapOffsets(
        text, startOffset, endOffset, fontType, (prevLineIndentInColumns + indentInColumns) * spaceWidth
      );
      if (softWrapOffsetsToUse == null) {
        softWrapOffsetsToUse = offsets;
        indentInColumnsToUse = indentInColumns;
        continue;
      }
      if (softWrapOffsetsToUse.size() > offsets.size()) {
        softWrapOffsetsToUse = offsets;
        indentInColumnsToUse = indentInColumns;
      }
    }

    if (indentInColumnsToUse <= 0) {
      processLogicalLine(text, line, fontType, IndentType.NONE, temporary);
    }
    else {
      registerSoftWraps(softWrapOffsetsToUse, indentInColumnsToUse + prevLineIndentInColumns, temporary);
    }
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  private TIntArrayList calculateSoftWrapOffsets(CharSequence text, int start, int end, int fontType, int reservedWidth) {
    TIntArrayList result = new TIntArrayList();

    // Find offsets where soft wraps should be applied for the logical line in case of no indent usage.
    int x = 0;
    int beforeSoftWrapDrawingWidth = myPainter.getMinDrawingWidth(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
    int prevSoftWrapOffset = start;
    CharBuffer buffer = CharBuffer.wrap(text);
    for (int i = start; i < end; i++) {
      int symbolWidth = myTextRepresentationHelper.textWidth(buffer, i, i + 1, fontType, x);
      if (x + symbolWidth + beforeSoftWrapDrawingWidth >= myVisibleAreaWidth) {
        int offset = calculateSoftWrapOffset(text, i, prevSoftWrapOffset, end);
        if (offset >= end || offset <= prevSoftWrapOffset) {
          // There is no way to insert soft wrap.
          return result;
        }
        result.add(offset);
        i = offset - 1; // Subtract one because of loop increment.
        prevSoftWrapOffset = offset;
        x = myPainter.getMinDrawingWidth(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED)
            + myPainter.getMinDrawingWidth(SoftWrapDrawingType.AFTER_SOFT_WRAP) + reservedWidth;
        continue;
      }
      x += symbolWidth;
    }
    return result;
  }

  private int getIndentSize() {
    Project project = myEditor.getProject();
    if (project == null) return DEFAULT_INDENT_SIZE;
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
    if (settings == null) return DEFAULT_INDENT_SIZE;
    VirtualFile file = myEditor.getVirtualFile();
    if (file == null) return DEFAULT_INDENT_SIZE;
    return settings.getIndentSize(file.getFileType());
  }

  private void registerSoftWraps(TIntArrayList offsets, int indentInColumns, boolean temporary) {
    for (int i = 0; i < offsets.size(); i++) {
      int offset = offsets.getQuick(i);
      myStorage.storeOrReplace(new TextChangeImpl("\n" + StringUtil.repeatSymbol(' ', indentInColumns), offset), !temporary);
    }
  }

  /**
   * Calculates offset to use for soft wrap appliance from the given <code>(min; max]</code> interval.
   * <p/>
   * <b>Note:</b> this method tries its best in order to define language/file type-agnostic mechanism for
   * soft wrap position determination. Feel free to define it as extension point if it's not possible to use
   * general algorithm.
   *
   * @param text        target text holder
   * @param preferred   preferred position to use for soft wrapping. Implies that all symbols from <code>(preferred; max)</code>
   *                    will be represented beyond the visible area, i.e. current method should try to find wrapping point
   *                    at <code>(min; preferred]</code> interval;
   * @param min         min offset to use (exclusive)
   * @param max         max offset to use (inclusive)
   * @return            wrapping offset to use (given <code>'max'</code> value should be returned if no more suitable point is found)
   */
  private static int calculateSoftWrapOffset(CharSequence text, int preferred, int min, int max) {
    // Try to find target offset that is not greater than preferred position.
    for (int i = preferred; i > min; i--) {
      char c = text.charAt(i);

      if (WHITE_SPACES.contains(c)) {
        return i < preferred ? i + 1 : i;
      }

      // Don't wrap on the non-id symbol preceded by another non-id symbol. E.g. consider that we have a statement
      // like 'foo(int... args)'. We don't want to wrap on the second or third dots then.
      if (i > min + 1 && !isIdSymbol(c) && !isIdSymbol(text.charAt(i - 1))) {
        continue;
      }
      if (SPECIAL_SYMBOLS_TO_WRAP_AFTER.contains(c)) {
        if (i < preferred) {
          return i + 1;
        }
        continue;
      }
      if (SPECIAL_SYMBOLS_TO_WRAP_BEFORE.contains(c) || WHITE_SPACES.contains(c)) {
        return i;
      }

      // Don't wrap on a non-id symbol followed by non-id symbol, e.g. don't wrap between two pluses at i++.
      // Also don't wrap before non-id symbol preceded by a space - wrap on space instead;
      if (!isIdSymbol(c) && (i < min + 2 || (isIdSymbol(text.charAt(i - 1)) && !WHITE_SPACES.contains(text.charAt(i - 1))))) {
        return i;
      }
    }

    // Try to find target offset that is greater than preferred position.
    for (int i = preferred + 1; i < max; i++) {
      char c = text.charAt(i);
      if (WHITE_SPACES.contains(c)) {
        return i;
      }
      // Don't wrap on the non-id symbol preceded by another non-id symbol. E.g. consider that we have a statement
      // like 'foo(int... args)'. We don't want to wrap on the second or third dots then.
      if (i < max - 1 && !isIdSymbol(c) && !isIdSymbol(text.charAt(i + 1)) && !isIdSymbol(text.charAt(i - 1))) {
        continue;
      }
      if (SPECIAL_SYMBOLS_TO_WRAP_BEFORE.contains(c)) {
        return i;
      }
      if (SPECIAL_SYMBOLS_TO_WRAP_AFTER.contains(c) && i < max - 1) {
        return i + 1;
      }

      // Don't wrap on a non-id symbol followed by non-id symbol, e.g. don't wrap between two pluses at i++;
      if (!isIdSymbol(c) && (i >= max - 1 || isIdSymbol(text.charAt(i + 1)))) {
        return i;
      }
    }
    return max;
  }

  private static boolean isIdSymbol(char c) {
    return c == '_' || c == '$' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }
}

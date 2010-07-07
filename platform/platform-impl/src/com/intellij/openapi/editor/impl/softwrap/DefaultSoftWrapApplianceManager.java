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
import com.intellij.openapi.editor.TextChange;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

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
     * Tries to indents soft-wrapped line start to the location of the first non white-space symbols of the previous visual line.
     * <p/>
     * Falls back to {@link #NONE} if indentation to the previous visual line start is considered to be unappropriated.
     */
    TO_PREV_LINE_NON_WS_START
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

  private final TIntHashSet myProcessedLogicalLines = new TIntHashSet();

  private final SoftWrapsStorage myStorage;
  private final EditorEx         myEditor;
  private final SoftWrapPainter  myPainter;

  private int myVisibleAreaWidth;

  public DefaultSoftWrapApplianceManager(SoftWrapsStorage storage, EditorEx editor, SoftWrapPainter painter) {
    myStorage = storage;
    myEditor = editor;
    myPainter = painter;
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  @Override
  public void registerSoftWrapIfNecessary(@NotNull char[] chars, int start, int end, int x, int fontType) {
    dropDataIfNecessary();

    if (start >= end) {
      return;
    }

    Document document = myEditor.getDocument();
    int startLine = document.getLineNumber(start);
    int endLine = document.getLineNumber(end);
    for (int i = startLine; i <= endLine; i++) {
      if (!myProcessedLogicalLines.contains(i)) {
        processLogicalLine(chars, i, fontType, IndentType.TO_PREV_LINE_NON_WS_START);
        myProcessedLogicalLines.add(i);
      }
    }
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

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  private void processLogicalLine(char[] text, int line, int fontType, IndentType indentType) {
    Document document = myEditor.getDocument();
    int startOffset = document.getLineStartOffset(line);
    int endOffset = document.getLineEndOffset(line);

    if (indentType == IndentType.NONE) {
      TIntArrayList offsets = calculateSoftWrapOffsets(text, startOffset, endOffset, fontType, 0);
      registerSoftWraps(offsets, 0);
      return;
    }

    // Understand if it's worth to define indent for soft wrap(s) to create and perform their actual construction and registration.
    int spaceWidth = EditorUtil.getSpaceWidth(fontType, myEditor);
    int indentInColumns = getIndentSize();
    VisualPosition visual = myEditor.offsetToVisualPosition(startOffset);
    int prevLineIndentInColumns = EditorActionUtil.findFirstNonSpaceColumnOnTheLine(myEditor, visual.line);
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
      processLogicalLine(text, line, fontType, IndentType.NONE);
    }
    else {
      registerSoftWraps(softWrapOffsetsToUse, indentInColumnsToUse + prevLineIndentInColumns);
    }
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  private TIntArrayList calculateSoftWrapOffsets(char[] text, int start, int end, int fontType, int reservedWidth) {
    TIntArrayList result = new TIntArrayList();

    // Find offsets where soft wraps should be applied for the logical line in case of no indent usage.
    int x = myPainter.getMinDrawingWidth(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
    int prevSoftWrapOffset = start;
    for (int i = start; i < end; i++) {
      int symbolWidth = EditorUtil.textWidth(myEditor, text, i, i + 1, fontType);
      if (x + symbolWidth >= myVisibleAreaWidth) {
        int offset = calculateSoftWrapOffset(text, i - 1, prevSoftWrapOffset, end);
        if (offset >= end || offset <= prevSoftWrapOffset) {
          // There is no way to insert soft wrap.
          return result;
        }
        result.add(offset);
        i = offset - 1; // Subtract one because of loop increment.
        prevSoftWrapOffset = offset;
        x = myPainter.getMinDrawingWidth(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED)
            + myPainter.getMinDrawingWidth(SoftWrapDrawingType.AFTER_SOFT_WRAP_LINE_FEED) + reservedWidth;
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

  private void registerSoftWraps(TIntArrayList offsets, int indentInColumns) {
    for (int i = 0; i < offsets.size(); i++) {
      int offset = offsets.getQuick(i);
      myStorage.storeSoftWrap(new TextChange("\n" + StringUtil.repeatSymbol(' ', indentInColumns), offset));
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
  private static int calculateSoftWrapOffset(char[] text, int preferred, int min, int max) {
    // Try to find target offset that is not greater than preferred position.
    for (int i = preferred; i > min; i--) {
      char c = text[i];
      if ((i < preferred) && (SPECIAL_SYMBOLS_TO_WRAP_AFTER.contains(c) || WHITE_SPACES.contains(c))) {
        return i + 1;
      }
      if (SPECIAL_SYMBOLS_TO_WRAP_BEFORE.contains(c) || WHITE_SPACES.contains(c)) {
        return i;
      }

      // Don't wrap on a non-id symbol followed by non-id symbol, e.g. don't wrap between two pluses at i++;
      if (!isIdSymbol(c) && (i < min + 2 || isIdSymbol(text[i - 1]))) {
        return i;
      }
    }

    // Try to find target offset that is greater than preferred position.
    for (int i = preferred + 1; i < max; i++) {
      char c = text[i];
      if (SPECIAL_SYMBOLS_TO_WRAP_BEFORE.contains(c) || WHITE_SPACES.contains(c)) {
        return i;
      }
      if (SPECIAL_SYMBOLS_TO_WRAP_AFTER.contains(c) && i < max - 1) {
        return i + 1;
      }

      // Don't wrap on a non-id symbol followed by non-id symbol, e.g. don't wrap between two pluses at i++;
      if (!isIdSymbol(c) && (i >= max || isIdSymbol(text[i + 1]))) {
        return i;
      }
    }
    return max;
  }

  private static boolean isIdSymbol(char c) {
    return c == '_' || c == '$' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }
}

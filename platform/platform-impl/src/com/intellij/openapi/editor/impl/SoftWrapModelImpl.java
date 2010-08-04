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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.SoftWrapChangeListener;
import com.intellij.openapi.editor.ex.SoftWrapModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.softwrap.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * Default {@link SoftWrapModelEx} implementation.
 * <p/>
 * Works as a mix of <code>GoF Facade and Bridge</code>, i.e. delegates the processing to the target sub-components and provides
 * utility methods built on top of sub-components API.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jun 8, 2010 12:47:32 PM
 */
public class SoftWrapModelImpl implements SoftWrapModelEx {

  private final SoftWrapDataMapper            myDataMapper;
  private final SoftWrapsStorage              myStorage;
  private final SoftWrapPainter               myPainter;
  private final SoftWrapApplianceManager      myApplianceManager;
  private final SoftWrapDocumentChangeManager myDocumentChangeManager;

  private final EditorEx myEditor;
  /** Holds number of 'active' calls, i.e. number of methods calls of the current object within the current call stack. */
  private       int      myActive;

  public SoftWrapModelImpl(@NotNull EditorEx editor) {
    this(editor, new SoftWrapsStorage(), new CompositeSoftWrapPainter(editor));
  }

  public SoftWrapModelImpl(@NotNull final EditorEx editor, @NotNull SoftWrapsStorage storage, @NotNull SoftWrapPainter painter) {
    this(
      editor, storage, painter, new DefaultSoftWrapApplianceManager(storage, editor, painter),
      new SoftWrapDataMapper(editor, storage, new DefaultEditorTextRepresentationHelper(editor)),
      new SoftWrapDocumentChangeManager(editor, storage)
    );
  }

  public SoftWrapModelImpl(@NotNull EditorEx editor, @NotNull SoftWrapsStorage storage, @NotNull SoftWrapPainter painter,
                           @NotNull SoftWrapApplianceManager applianceManager, @NotNull SoftWrapDataMapper dataMapper,
                           @NotNull SoftWrapDocumentChangeManager documentChangeManager)
  {
    myEditor = editor;
    myStorage = storage;
    myPainter = painter;
    myApplianceManager = applianceManager;
    myDataMapper = dataMapper;
    myDocumentChangeManager = documentChangeManager;
  }

  public boolean isSoftWrappingEnabled() {
    return myEditor.getSettings().isUseSoftWraps() && !myEditor.isOneLineMode();
  }

  @Nullable
  public TextChange getSoftWrap(int offset) {
    if (!isSoftWrappingEnabled()) {
      return null;
    }
    return myStorage.getSoftWrap(offset);
  }

  @Override
  public int getSoftWrapIndex(int offset) {
    return myStorage.getSoftWrapIndex(offset);
  }

  @NotNull
  @Override
  public List<? extends TextChange> getSoftWrapsForRange(int start, int end) {
    if (!isSoftWrappingEnabled()) {
      return Collections.emptyList();
    }
    int startIndex = myStorage.getSoftWrapIndex(start);
    if (startIndex < 0) {
      startIndex = -startIndex - 1;
    }

    List<TextChangeImpl> softWraps = myStorage.getSoftWraps();
    if (startIndex >= softWraps.size()) {
      return Collections.emptyList();
    }

    int endIndex = myStorage.getSoftWrapIndex(end);
    if (endIndex < 0) {
      endIndex = -endIndex - 1;
    }
    endIndex = Math.min(softWraps.size(), endIndex);

    if (endIndex > startIndex) {
      return softWraps.subList(startIndex, endIndex);
    }
    else {
      return Collections.emptyList();
    }
  }

  @Override
  @NotNull
  public List<? extends TextChange> getSoftWrapsForLine(int documentLine) {
    if (!isSoftWrappingEnabled()) {
      return Collections.emptyList();
    }
    Document document = myEditor.getDocument();
    int start = document.getLineStartOffset(documentLine);
    int end = document.getLineEndOffset(documentLine);
    return getSoftWrapsForRange(start, end);
  }

  /**
   * @return    total number of soft wrap-introduced new visual lines
   */
  public int getSoftWrapsIntroducedLinesNumber() {
    if (!isSoftWrappingEnabled()) {
      return 0;
    }
    int result = 0;
    FoldingModel foldingModel = myEditor.getFoldingModel();
    for (TextChange softWrap : myStorage.getSoftWraps()) {
      if (!foldingModel.isOffsetCollapsed(softWrap.getStart())) {
        result += StringUtil.countNewLines(softWrap.getText());
      }
    }
    return result;
  }

  public void registerSoftWrapIfNecessary(@NotNull char[] chars, int start, int end, int x, int fontType) {
    if (!isSoftWrappingEnabled()) {
      return;
    }
    myDocumentChangeManager.syncSoftWraps();

    myActive++;
    try {
      myApplianceManager.registerSoftWrapIfNecessary(chars, start, end, x, fontType);
    }
    finally {
      myActive--;
    }
  }

  @Override
  public List<? extends TextChange> getRegisteredSoftWraps() {
    if (!isSoftWrappingEnabled()) {
      return Collections.emptyList();
    }
    return myStorage.getSoftWraps();
  }

  @Override
  public boolean isVisible(TextChange softWrap) {
    FoldingModel foldingModel = myEditor.getFoldingModel();
    int start = softWrap.getStart();
    if (!foldingModel.isOffsetCollapsed(start)) {
      return false;
    }

    // There is a possible case that soft wrap and collapsed folding region share the same offset, i.e. soft wrap is represented
    // before the folding. We need to return 'true' in such situation. Hence, we check if offset just before the soft wrap
    // is collapsed as well.
    return start <= 0 || !foldingModel.isOffsetCollapsed(start - 1);
  }

  @Override
  public int paint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
    if (!isSoftWrappingEnabled()) {
      return 0;
    }
    return myPainter.paint(g, drawingType, x,  y, lineHeight);
  }

  @Override
  public int getMinDrawingWidthInPixels(@NotNull SoftWrapDrawingType drawingType) {
    return myPainter.getMinDrawingWidth(drawingType);
  }

  @Override
  public int getMinDrawingWidthInColumns(@NotNull SoftWrapDrawingType drawingType) {
    return myPainter.getMinDrawingWidth(drawingType) > 0 ? 1 : 0;
  }

  @NotNull
  @Override
  public LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visual) {
    if (myActive > 0 || !isSoftWrappingEnabled() || myStorage.isEmpty() || myEditor.getDocument().getTextLength() <= 0) {
      return myEditor.visualToLogicalPosition(visual, false);
    }
    myActive++;
    try {
      return myDataMapper.visualToLogical(visual);
    } finally {
      myActive--;
    }
  }

  @NotNull
  @Override
  public LogicalPosition offsetToLogicalPosition(int offset) {
    if (myActive > 0 || !isSoftWrappingEnabled() || myStorage.isEmpty() || myEditor.getDocument().getTextLength() <= 0) {
      return myEditor.offsetToLogicalPosition(offset, false);
    }
    myActive++;
    try {
      return myDataMapper.offsetToLogicalPosition(offset);
    } finally {
      myActive--;
    }
  }

  @NotNull
  public LogicalPosition adjustLogicalPosition(LogicalPosition defaultLogical, int offset) {
    if (myActive > 0 || !isSoftWrappingEnabled()) {
      return defaultLogical;
    }

    myActive++;
    try {
      return myDataMapper.offsetToLogicalPosition(offset);
    } finally {
      myActive--;
    }
  }

  @NotNull
  public VisualPosition adjustVisualPosition(@NotNull LogicalPosition logical, @NotNull VisualPosition defaultVisual) {
    if (myActive > 0 || !isSoftWrappingEnabled() || myStorage.isEmpty()) {
      return defaultVisual;
    }

    myActive++;
    try {
      return myDataMapper.adjustVisualPosition(logical, defaultVisual);
    }
    finally {
      myActive--;
    }
  }

  @Override
  public boolean isInsideSoftWrap(@NotNull VisualPosition visual) {
    if (!isSoftWrappingEnabled()) {
      return false;
    }
    LogicalPosition logical = myEditor.visualToLogicalPosition(visual);
    int offset = myEditor.logicalPositionToOffset(logical);
    if (offset <= 0) {
      // Never expect to be here, just a defensive programming.
      return false;
    }

    TextChange softWrap = getSoftWrap(offset);
    if (softWrap == null) {
      return false;
    }

    // We consider visual positions that point after the last symbol before soft wrap and the first symbol after soft wrap to not
    // belong to soft wrap-introduced virtual space.
    VisualPosition visualAfterSoftWrap = myEditor.offsetToVisualPosition(offset);
    if (visualAfterSoftWrap.line == visual.line && visualAfterSoftWrap.column <= visual.column) {
      return false;
    }

    VisualPosition visualBeforeSoftWrap = myEditor.offsetToVisualPosition(offset - 1);
    int columnOffset = 0;
    LogicalPosition logLineStart = myEditor.visualToLogicalPosition(new VisualPosition(visualBeforeSoftWrap.line, 0));
    if (logLineStart.softWrapLinesOnCurrentLogicalLine > 0) {
      int offsetLineStart = myEditor.logicalPositionToOffset(logLineStart);
      softWrap = getSoftWrap(offsetLineStart);
      if (softWrap != null) {
        columnOffset = getSoftWrapIndentWidthInColumns(softWrap);
      }
    }
    int width = EditorUtil.textWidthInColumns(myEditor, myEditor.getDocument().getCharsSequence(), offset - 1, offset, columnOffset);
    int softWrapStartColumn = visualBeforeSoftWrap.column  + width;
    return visual.line > visualBeforeSoftWrap.line || visual.column > softWrapStartColumn;
  }

  @Override
  public int getSoftWrapIndentWidthInPixels(@NotNull TextChange softWrap) {
    if (!isSoftWrappingEnabled()) {
      return 0;
    }
    char[] chars = softWrap.getChars();
    int result = myPainter.getMinDrawingWidth(SoftWrapDrawingType.AFTER_SOFT_WRAP);

    int start = 0;
    int end = chars.length;

    int i = CharArrayUtil.lastIndexOf(chars, '\n', 0, chars.length);
    if (i >= 0) {
      start = i + 1;
    }

    if (start < end) {
      result += EditorUtil.textWidth(myEditor, softWrap.getText(), start, end, Font.PLAIN, 0);
    }

    return result;
  }

  @Override
  public int getSoftWrapIndentWidthInColumns(@NotNull TextChange softWrap) {
    if (!isSoftWrappingEnabled()) {
      return 0;
    }
    char[] chars = softWrap.getChars();
    int result = 1; // For 'after soft wrap' drawing

    int start = 0;
    int i = CharArrayUtil.lastIndexOf(chars, '\n', 0, chars.length);
    if (i >= 0) {
      start = i + 1;
    }
    result += chars.length - start;

    return result;
  }

  public void beforeDocumentChangeAtCaret() {
    int offset = myEditor.getCaretModel().getOffset();
    TextChangeImpl softWrap = myStorage.getSoftWrap(offset);
    if (softWrap == null) {
      return;
    }

    VisualPosition visualCaretPosition = myEditor.getCaretModel().getVisualPosition();
    myDocumentChangeManager.makeHardWrap(softWrap);

    // Restore caret position.
    myEditor.getCaretModel().moveToVisualPosition(visualCaretPosition);
  }

  @Override
  public boolean addSoftWrapChangeListener(@NotNull SoftWrapChangeListener listener) {
    return myStorage.addSoftWrapChangeListener(listener);
  }
}

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

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * Encapsulates logic of various mappings (e.g. {@code 'offset -> logical position'}) and adjustments (e.g. adjust soft wrap unaware
 * logical position for the target visual position).
 *
 * @author Denis Zhdanov
 * @since Jul 7, 2010 2:31:04 PM
 */
public class SoftWrapDataMapper {

  private final EditorTextRepresentationHelper myTextRepresentationHelper;
  private final EditorEx                       myEditor;
  private final SoftWrapsStorage               myStorage;
  //private final FontTypeProvider               myFontTypeProvider;

  //public SoftWrapDataMapper(EditorEx editor,
  //                          SoftWrapsStorage storage,
  //                          EditorTextRepresentationHelper textRepresentationHelper)
  //{
  //  this(editor, storage, textRepresentationHelper, new IterationStateFontTypeProvider(editor));
  //}

  public SoftWrapDataMapper(EditorEx editor,
                            SoftWrapsStorage storage,
                            EditorTextRepresentationHelper textRepresentationHelper/*,
                            FontTypeProvider fontTypeProvider*/)
  {
    myEditor = editor;
    myStorage = storage;
    myTextRepresentationHelper = textRepresentationHelper;
    //myFontTypeProvider = fontTypeProvider;
  }

  @NotNull
  public LogicalPosition visualToLogical(@NotNull VisualPosition visual) {
    return toLogical(new VisualPositionBasedStrategy(visual));
  }

  @NotNull
  public LogicalPosition offsetToLogicalPosition(int offset) {
    OffsetBasedStrategy strategy = new OffsetBasedStrategy(myTextRepresentationHelper, myEditor.getDocument(), offset);
    return toLogical(strategy);
  }

  @NotNull
  private LogicalPosition toLogical(LogicalPositionCalculatorStrategy strategy) {
    LogicalPositionCalculator calculator = new LogicalPositionCalculator(strategy);
    return calculator.calculate();
  }

  @NotNull
  public VisualPosition adjustVisualPosition(@NotNull LogicalPosition logical, @NotNull VisualPosition visual) {
    if (logical.visualPositionAware) {
      // We don't need to recalculate logical position adjustments because given object already has them.
      return logical.toVisualPosition();
    }

    List<TextChangeImpl> softWraps = myStorage.getSoftWraps();

    // Check if there are registered soft wraps before the target logical position.
    int maxOffset = myEditor.logicalPositionToOffset(logical);
    int endIndex = myStorage.getSoftWrapIndex(maxOffset);
    if (endIndex < 0) {
      endIndex = -endIndex - 2; // We subtract '2' instead of '1' here in order to point to offset of the first soft wrap the
      // is located before the given logical position.
    }

    // Return eagerly if no soft wraps are registered before the target offset.
    if (endIndex < 0 || endIndex >= softWraps.size()) {
      return visual;
    }

    int lineDiff = 0;
    int column = -1;

    int targetLogicalLineStartOffset = myEditor.logicalPositionToOffset(new LogicalPosition(logical.line, 0));
    for (int i = endIndex; i >= 0; i--) {
      TextChange softWrap = softWraps.get(i);
      if (softWrap == null) {
        assert false;
        continue;
      }

      if (!isVisible(softWrap)) {
        continue;
      }

      CharSequence softWrapText = softWrap.getText();
      int softWrapLines = StringUtil.countNewLines(softWrapText);

      // Count lines introduced by the current soft wrap. We assume that the soft wrap is located before target offset,
      // hence, we're free to count all of its line feeds.
      lineDiff += softWrapLines;

      // Count soft wrap column offset only if it's located at the same line as the target offset.
      if (softWrapLines > 0 && softWrap.getStart() >= targetLogicalLineStartOffset) {
        for (int j = softWrapText.length() - 1; j >= 0; j--) {
          if (softWrapText.charAt(j) == '\n') {
            column = maxOffset - softWrap.getStart() - j + 1;
            break;
          }
        }
      }
    }

    int columnToUse = column >= 0 ? column : visual.column;
    return new VisualPosition(visual.line + lineDiff, columnToUse);
  }

  private boolean isVisible(TextChange softWrap) {
    FoldingModel foldingModel = myEditor.getFoldingModel();
    int start = softWrap.getStart();

    // There is a possible case that folding region starts just after soft wrap, i.e. soft wrap and folding region share the
    // same offset. However, soft wrap is shown, hence, we also check offset just before the target one.
    return !foldingModel.isOffsetCollapsed(start) || !foldingModel.isOffsetCollapsed(start - 1);
  }

  private static class Context implements Cloneable {

    public int logicalLine;
    public int logicalColumn;
    public int visualLine;
    public int visualColumn;
    public int offset;
    public int softWrapLinesBefore;
    public int softWrapLinesCurrent;
    public int softWrapColumnDiff;
    public int foldedLines;
    public int foldingColumnDiff;
    public int x;

    @NotNull
    public LogicalPosition build() {
      return new LogicalPosition(
        logicalLine, logicalColumn, softWrapLinesBefore, softWrapLinesCurrent, softWrapColumnDiff, foldedLines, foldingColumnDiff
      );
    }

    @Override
    protected Context clone() {
      Context result = new Context();
      result.logicalLine = logicalLine;
      result.logicalColumn = logicalColumn;
      result.visualLine = visualLine;
      result.visualColumn = visualColumn;
      result.offset = offset;
      result.softWrapLinesBefore = softWrapLinesBefore;
      result.softWrapLinesCurrent = softWrapLinesCurrent;
      result.softWrapColumnDiff = softWrapColumnDiff;
      result.foldedLines = foldedLines;
      result.foldingColumnDiff = foldingColumnDiff;
      result.x = x;
      return result;
    }

    private void onNewLine() {
      softWrapLinesBefore += softWrapLinesCurrent;
      softWrapLinesCurrent = 0;
      softWrapColumnDiff = 0;
      foldingColumnDiff = 0;
      x = 0;
    }
  }

  private class LogicalPositionCalculator {

    public final LogicalPositionCalculatorStrategy strategy;

    public Context context = new Context();

    LogicalPositionCalculator(LogicalPositionCalculatorStrategy strategy) {
      this.strategy = strategy;
    }

    @NotNull
    public LogicalPosition calculate() {
      FoldingProvider foldRegions = new FoldingProvider();
      SoftWrapsProvider softWraps = new SoftWrapsProvider();

      FoldRegion foldRegion = foldRegions.get();
      TextChange softWrap = softWraps.get();

      LogicalPosition result = null;
      while (true) {
        if (foldRegion == null && softWrap == null || strategy.exceeds(context)) {
          return strategy.build(context);
        }

        if (foldRegion != null && softWrap != null) {
          if (softWrap.getStart() <= foldRegion.getStartOffset()) {
            result = process(softWrap);
            softWrap = softWraps.get();
          }
          else {
            result = process(foldRegion);
            foldRegion = foldRegions.get();
          }
        }
        else {
          if (foldRegion != null) {
            result = process(foldRegion);
            foldRegion = foldRegions.get();
          }
          if (softWrap != null) {
            result = process(softWrap);
            softWrap = softWraps.get();
          }
        }
        if (result != null) {
          return result;
        }
      }
    }

    @Nullable
    private LogicalPosition process(@NotNull FoldRegion region) {
      int endDocumentOffset = myEditor.getDocument().getTextLength();
      if (region.getEndOffset() >= endDocumentOffset) {
        return advanceToOffset(endDocumentOffset).build();
      }
      if (region.getStartOffset() > context.offset) {
        Context newContext = advanceToOffset(region.getStartOffset());
        if (strategy.exceeds(newContext)) {
          return strategy.build(context);
        }
        context = newContext;
      }

      Document document = myEditor.getDocument();
      CharSequence text = document.getCharsSequence();
      int foldingStartLine = document.getLineNumber(region.getStartOffset());

      Context afterFolding = context.clone();
      afterFolding.logicalLine += document.getLineNumber(region.getEndOffset()) - foldingStartLine;
      int visualColumnInc = region.getPlaceholderText().length();  // Assuming that no tabulations are used at placeholder.
      afterFolding.visualColumn += visualColumnInc;

      int i = CharArrayUtil.shiftBackwardUntil(text, region.getEndOffset() - 1, "\n");
      // Process multi-line folding.
      if (i >= region.getStartOffset()) {
        afterFolding.x = myTextRepresentationHelper.textWidth(text, i + 1, region.getEndOffset(), Font.PLAIN, 0);
        afterFolding.logicalColumn = myTextRepresentationHelper.toVisualColumnSymbolsNumber(text, i + 1, region.getEndOffset(), 0);
        afterFolding.softWrapLinesBefore += afterFolding.softWrapLinesCurrent;
        afterFolding.softWrapLinesCurrent = 0;
        afterFolding.softWrapColumnDiff = 0;
        afterFolding.foldedLines += document.getLineNumber(region.getEndOffset()) - foldingStartLine;
        afterFolding.foldingColumnDiff = afterFolding.visualColumn - afterFolding.logicalColumn;
      }
      // Process single-line folding
      else {
        int width = myTextRepresentationHelper.textWidth(text, region.getStartOffset(), region.getEndOffset(), Font.PLAIN, context.x);
        int logicalColumnInc = myTextRepresentationHelper.toVisualColumnSymbolsNumber(
          text, region.getStartOffset(), region.getEndOffset(), context.x
        );
        afterFolding.logicalColumn += logicalColumnInc;
        afterFolding.x += width;
        afterFolding.foldingColumnDiff += visualColumnInc - logicalColumnInc;
      }
      afterFolding.offset = region.getEndOffset();

      if (!strategy.exceeds(afterFolding)) {
        context = afterFolding;
        return null;
      }

      return strategy.build(context, region);
    }

    @Nullable
    private LogicalPosition process(@NotNull TextChange softWrap) {
      int endDocumentOffset = myEditor.getDocument().getTextLength();
      if (softWrap.getStart() >= endDocumentOffset) {
        return strategy.build(context);
      }
      Context newContext = advanceToOffset(softWrap.getStart());
      if (strategy.exceeds(newContext)) {
        return strategy.build(context);
      }
      Document document = myEditor.getDocument();
      int lastUsedLogicalLine = document.getLineNumber(context.offset);
      context = newContext;

      // Create context that points to the soft wrap end visual position.
      Context afterSoftWrap = context.clone();
      int lineFeeds = StringUtil.countNewLines(softWrap.getText());
      afterSoftWrap.visualLine += lineFeeds;
      afterSoftWrap.visualColumn = myEditor.getSoftWrapModel().getSoftWrapIndentWidthInColumns(softWrap);
      afterSoftWrap.x = myEditor.getSoftWrapModel().getSoftWrapIndentWidthInPixels(softWrap);
      if (lastUsedLogicalLine == context.logicalLine) {
        afterSoftWrap.softWrapLinesCurrent += lineFeeds;
      }
      else {
        afterSoftWrap.softWrapLinesBefore += context.softWrapLinesCurrent;
        afterSoftWrap.softWrapLinesCurrent = lineFeeds;
      }
      afterSoftWrap.softWrapColumnDiff = afterSoftWrap.visualColumn - afterSoftWrap.logicalColumn;
      afterSoftWrap.foldingColumnDiff = 0;

      if (!strategy.exceeds(afterSoftWrap)) {
        context = afterSoftWrap;
        return null;
      }
      return strategy.build(context, softWrap);
    }

    private Context advanceToOffset(int newOffset) {
      Context result = context.clone();
      if (result.offset == newOffset) {
        return result;
      }

      Document document = myEditor.getDocument();
      CharSequence text = document.getCharsSequence();
      int lastUsedLogicalLine = document.getLineNumber(context.offset);
      int currentLogicalLine = document.getLineNumber(newOffset);

      // Update state to the offset that corresponds to the same logical line that was used last time.
      if (currentLogicalLine == lastUsedLogicalLine) {
        int columnDiff = myTextRepresentationHelper.toVisualColumnSymbolsNumber(text, result.offset, newOffset, result.x);
        result.logicalColumn += columnDiff;
        result.visualColumn += columnDiff;
        if (strategy.recalculateX(result)) {
          result.x += myTextRepresentationHelper.textWidth(text, result.offset, newOffset, Font.PLAIN, result.x);
        }
      }
      // Update state to offset that doesn't correspond to the same logical line that was used last time.
      else {
        int lineDiff = currentLogicalLine - lastUsedLogicalLine;
        result.logicalLine += lineDiff;
        result.visualLine += lineDiff;
        int startLineOffset = document.getLineStartOffset(currentLogicalLine);
        result.visualColumn = myTextRepresentationHelper.toVisualColumnSymbolsNumber(text, startLineOffset, newOffset, 0);
        result.logicalColumn = result.visualColumn;
        result.onNewLine();
        if (strategy.recalculateX(result)) {
          result.x = myTextRepresentationHelper.textWidth(text, startLineOffset, newOffset, Font.PLAIN, 0);
        }
      }
      result.offset = newOffset;
      return result;
    }
  }

  /**
   * Strategy interface for performing logical position calculation.
   */
  private interface LogicalPositionCalculatorStrategy {
    boolean exceeds(Context context);

    /**
     * Profiling shows that visual symbol width calculation (necessary for <code>'x'</code> coordinate update) is quite expensive.
     * However, the whole logical position calculation algorithm contains many iterations and there is a big chance that many of them
     * don't require <code>'x'</code> recalculation (e.g. we may map visual position to logical and see that current context visual
     * line is less than the target, hence, we understand that 'x' coordinate will be reset to zero).
     * <p/>
     * This method allows to answer if we need to perform <code>'x'</code> recalculation for the given context (assuming that all
     * its another parameters are up-to-date).
     *
     * @param context
     * @return
     */
    boolean recalculateX(Context context);
    @NotNull LogicalPosition build(Context context);
    @NotNull LogicalPosition build(Context context, FoldRegion region);
    @NotNull LogicalPosition build(Context context, TextChange softWrap);
  }

  private static class VisualPositionBasedStrategy implements LogicalPositionCalculatorStrategy {

    private final VisualPosition myTargetVisual;

    VisualPositionBasedStrategy(VisualPosition visual) {
      myTargetVisual = visual;
    }

    @Override
    public boolean exceeds(Context context) {
      return context.visualLine > myTargetVisual.line
        || (context.visualLine == myTargetVisual.line && context.visualColumn > myTargetVisual.column);
    }

    @Override
    public boolean recalculateX(Context context) {
      return context.visualLine == myTargetVisual.line;
    }

    @NotNull
    @Override
    public LogicalPosition build(Context context) {
      if (context.visualLine == myTargetVisual.line) {
        context.logicalColumn += myTargetVisual.column - context.visualColumn;
        return context.build();
      }
      context.logicalLine += myTargetVisual.line - context.visualLine;
      context.logicalColumn = myTargetVisual.column;
      context.onNewLine();
      return context.build();
    }

    @NotNull
    @Override
    public LogicalPosition build(Context context, FoldRegion region) {
      // We just point to the logical position of folding region start if visual position points to collapsed fold region placeholder.
      return context.build();
    }

    @NotNull
    @Override
    public LogicalPosition build(Context context, TextChange softWrap) {
      if (myTargetVisual.line == context.visualLine) {
        context.softWrapColumnDiff = myTargetVisual.column - context.logicalColumn - context.foldingColumnDiff;
      }
      else {
        context.foldingColumnDiff = 0;
        context.softWrapLinesCurrent += myTargetVisual.line - context.visualLine;
        context.softWrapColumnDiff = myTargetVisual.column - context.logicalColumn;
      }
      return context.build();
    }
  }

  private static class OffsetBasedStrategy implements LogicalPositionCalculatorStrategy {

    private final EditorTextRepresentationHelper myRepresentationHelper;
    private final Document                       myDocument;

    private final int myOffset;

    OffsetBasedStrategy(EditorTextRepresentationHelper representationHelper, Document document, int offset) {
      myRepresentationHelper = representationHelper;
      myDocument = document;
      myOffset = offset;
    }

    @Override
    public boolean exceeds(Context context) {
      return context.offset > myOffset;
    }

    @Override
    public boolean recalculateX(Context context) {
      return true;
    }

    @NotNull
    @Override
    public LogicalPosition build(Context context) {
      int targetLogicalLine = myDocument.getLineNumber(myOffset);
      if (targetLogicalLine == context.logicalLine) {
        context.logicalColumn
          += myRepresentationHelper.toVisualColumnSymbolsNumber(myDocument.getCharsSequence(), context.offset, myOffset, context.x);
        return context.build();
      }
      context.logicalLine = targetLogicalLine;
      int i = CharArrayUtil.shiftBackwardUntil(myDocument.getCharsSequence(), myOffset - 1, "\n");
      if (i >= context.offset) {
        context.logicalColumn = myRepresentationHelper.toVisualColumnSymbolsNumber(myDocument.getCharsSequence(), i + 1, myOffset, 0);
      }
      else {
        context.logicalColumn
          = myRepresentationHelper.toVisualColumnSymbolsNumber(myDocument.getCharsSequence(), context.offset, myOffset, context.x);
      }
      context.onNewLine();
      return context.build();
    }

    @NotNull
    @Override
    public LogicalPosition build(Context context, FoldRegion region) {
      // We want to return logical position that corresponds to the visual start of the given folding region.
      int startLine = myDocument.getLineNumber(region.getStartOffset());
      int endLine = myDocument.getLineNumber(myOffset);
      int lineFeeds = endLine - startLine;

      if (lineFeeds > 0) {
        context.logicalLine += lineFeeds;
        context.foldedLines += lineFeeds;
        context.onNewLine();
        int i = CharArrayUtil.shiftBackwardUntil(myDocument.getCharsSequence(), myOffset - 1, "\n");
        context.logicalColumn = myRepresentationHelper.toVisualColumnSymbolsNumber(myDocument.getCharsSequence(), i + 1, myOffset, 0);
        context.foldingColumnDiff = context.visualColumn - context.logicalColumn;
      }
      else {
        int logicalColumns
          = myRepresentationHelper.toVisualColumnSymbolsNumber(myDocument.getCharsSequence(), region.getStartOffset(), myOffset, context.x);
        context.logicalColumn += logicalColumns;
        context.foldingColumnDiff -= logicalColumns;
      }
      return context.build();
    }

    @NotNull
    @Override
    public LogicalPosition build(Context context, TextChange softWrap) {
      assert false; // Don't expect soft wrap do affect offset-based mapping request.
      return new LogicalPosition(0, 0);
    }
  }

  /**
   * Strategy interface for providing font type to use during working with editor text.
   * <p/>
   * It's primary purpose is to relief unit testing.
   */
  //interface FontTypeProvider {
  //  void init(int start);
  //  int getFontType(int offset);
  //  void cleanup();
  //}

  //private static class IterationStateFontTypeProvider implements FontTypeProvider {
  //
  //  private final EditorEx myEditor;
  //
  //  private IterationState myState;
  //  private int            myFontType;
  //
  //  private IterationStateFontTypeProvider(EditorEx editor) {
  //    myEditor = editor;
  //  }
  //
  //  @Override
  //  public void init(int start) {
  //    myState = new IterationState(myEditor, start, false);
  //    myFontType = myState.getMergedAttributes().getFontType();
  //  }
  //
  //  @Override
  //  public int getFontType(int offset) {
  //    if (offset >= myState.getEndOffset()) {
  //      myState.advance();
  //      myFontType = myState.getMergedAttributes().getFontType();
  //    }
  //    return myFontType;
  //  }
  //
  //  @Override
  //  public void cleanup() {
  //    myState = null;
  //  }
  //}

  private class SoftWrapsProvider {

    private final List<? extends TextChange> mySoftWraps;
    private int myIndex;

    SoftWrapsProvider() {
      mySoftWraps = myStorage.getSoftWraps();
    }

    @Nullable
    public TextChange get() {
      if (myIndex < 0) {
        return null;
      }
      while (myIndex < mySoftWraps.size()) {
        TextChange result = mySoftWraps.get(myIndex++);
        if (isVisible(result)) {
          return result;
        }
      }
      return null;
    }
  }

  private class FoldingProvider {

    private final FoldRegion[] myFoldRegions;
    private int myIndex;

    FoldingProvider() {
      myFoldRegions = myEditor.getFoldingModel().fetchTopLevel();
    }

    @Nullable
    public FoldRegion get() {
      if (myFoldRegions == null || myIndex < 0) {
        return null;
      }
      while (myIndex < myFoldRegions.length) {
        FoldRegion result = myFoldRegions[myIndex++];
        if (result.isExpanded()) {
          return get();
        }
      }
      return null;
    }
  }
}
